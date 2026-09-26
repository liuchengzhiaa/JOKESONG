package com.example.asmrplayer.core.subtitles

import java.util.Locale

/**
 * One timed-text file found beside the audio, before anything has matched it.
 *
 * [sizeBytes] and [modifiedAtMs] travel with it because together they are the
 * identity of the file's contents: a cache keyed on the document URI alone would keep
 * serving the old lyrics after the file is edited in place, which is exactly what
 * happens when someone fixes a mistimed line.
 */
data class TimedTextCandidate(
    val displayName: String,
    val documentUri: String,
    val format: TimedTextFormat,
    val sizeBytes: Long,
    val modifiedAtMs: Long,
)

/** An audio file paired with the timed text that belongs to it. */
data class TimedTextMatch(
    /** The audio file's display name without its extension: what the scanner calls a title. */
    val audioTitle: String,
    val candidate: TimedTextCandidate,
    /** The suffix the timed-text file carried, or null when it had none. */
    val languageTag: String?,
)

/**
 * The shape of a language tag in a filename: `zh`, `zh-Hans`, `en-US`, `chi`.
 *
 * Two or three letters and optional subtags, and no more. A longer word after the dot
 * is part of the track's own name -- `Track.Part.01` is not a translation into a
 * language called "Part".
 */
private val LanguageTag = Regex("""^[A-Za-z]{2,3}(?:-[A-Za-z0-9]{1,8})*$""")

/**
 * Tags treated as the owner's language, because this is a Simplified-Chinese-first app
 * (D-009). Subtitle tools disagree about which code to write, so the ISO 639-1, the
 * bibliographic and the Mandarin codes are all listed.
 */
private val PreferredLanguageTags =
    setOf("zh", "zh-hans", "zh-hant", "zh-cn", "zh-tw", "chi", "zho", "cmn")

/**
 * Three-letter suffixes that fit the shape of a language tag but are a version marker
 * in practice: `Track.new.lrc` is the corrected file, not a Newari translation.
 *
 * ISO 639-3 really does have `new` (Newari) and `alt` (Southern Altai), so this list
 * can be wrong about a genuine translation. The cost is bounded by what the list
 * actually decides: a marker is peeled off and the lookup continues, so it only ever
 * changes which name a file resolves to, never whether it resolves at all.
 */
private val VersionMarkerSuffixes = setOf("mix", "alt", "new", "old", "dat", "bak")

/**
 * Pairs every audio file it can with the timed text sitting beside it.
 *
 * [audioTracks] maps the caller's own handle for each audio file to that file's title,
 * which is its display name with the extension removed. The scanner passes the
 * document URI, because that is what the player holds when it later asks for the
 * lyrics. The returned map is keyed by the same handle, so a lookup never needs a
 * second mapping step that could disagree with this one.
 *
 * Two files may share a title -- `Track.mp3` and `Track.flac` in one folder are the
 * same track in two formats -- and each of them gets the lyric file, because the
 * handle they are keyed by differs even when the title does not.
 *
 * The rule is the filename: `Track.lrc` belongs to `Track.mp3`, and `Track.zh.lrc`
 * belongs to it too with `zh` recorded as the language. Matching is case-insensitive,
 * because the same library is reachable over filesystems that disagree about case and
 * a lyric file that differs only in case is still that track's lyric file.
 *
 * A name that matches nothing is peeled one trailing segment at a time and looked up
 * again -- a language tag or a version marker is dropped, a segment that is neither
 * stops the search. That is what makes `Track.new.lrc` reach `Track` rather than being
 * discarded for having an unrecognised name, and it is bounded by the number of dots
 * in the name, so it can never run away.
 *
 * When one track has several candidates, the order below decides, and it is a product
 * order rather than an arbitrary one:
 *
 *  1. a file with no language tag, which is the one its author named after the track;
 *  2. a Chinese tag, because this library is Chinese (see [PreferredLanguageTags]);
 *  3. LRC over VTT over SRT, for the reason on [TimedTextFormat];
 *  4. the language tag and then the filename, so the result never depends on the order
 *     the document provider happened to list the folder in.
 *
 * A file that matches no audio is left out: timed text beside nothing is not something
 * this app can show, and pretending otherwise would put orphan files into the index.
 */
fun matchTimedText(
    audioTracks: Map<String, String>,
    candidates: List<TimedTextCandidate>,
): Map<String, TimedTextMatch> {
    val handlesByStem = mutableMapOf<String, MutableList<String>>()
    audioTracks.forEach { (handle, title) ->
        handlesByStem.getOrPut(title.lowercase(Locale.ROOT)) { mutableListOf() } += handle
    }

    val best = mutableMapOf<String, TimedTextMatch>()
    for (candidate in candidates) {
        val stem = candidate.displayName.substringBeforeLast('.', candidate.displayName)
        val resolution = resolveStem(stem, handlesByStem) ?: continue

        for (handle in resolution.handles) {
            val match = TimedTextMatch(
                audioTitle = audioTracks.getValue(handle),
                candidate = candidate,
                languageTag = resolution.languageTag,
            )
            val current = best[handle]
            if (current == null || isPreferred(match, current)) best[handle] = match
        }
    }
    return best
}

/** The audio this stem names, and the language it was translated into if any. */
private class StemResolution(val handles: List<String>, val languageTag: String?)

/**
 * Finds the audio titles a timed-text stem names, peeling trailing segments.
 *
 * Trimmed from the end inwards, because that is the direction the naming convention
 * runs: everything after the track's own name is decoration about the file, and a
 * segment that is neither a language nor a known marker is part of the name, so it
 * ends the search.
 */
private fun resolveStem(stem: String, handlesByStem: Map<String, List<String>>): StemResolution? {
    var current = stem
    var languageTag: String? = null
    // One peel per dot, so a pathological name terminates rather than looping.
    repeat(stem.count { it == '.' } + 1) {
        handlesByStem[current.lowercase(Locale.ROOT)]?.let { return StemResolution(it, languageTag) }
        val segment = trailingSegment(current) ?: return null
        when {
            isVersionMarker(segment) -> Unit
            isLanguageTag(segment) -> {
                // Two tags in one name -- `Track.zh.en.lrc` -- say nothing about which
                // language the listener wants, so the file is left unmatched rather
                // than guessed at.
                if (languageTag != null) return null
                languageTag = segment
            }
            else -> return null
        }
        current = current.dropLast(segment.length + 1)
    }
    return null
}

/** The trailing dot-separated segment of [stem], when it reads as a language tag. */
internal fun languageTagOf(stem: String): String? =
    trailingSegment(stem)?.takeIf(::isLanguageTag)

/** Everything after [stem]'s last dot, or null when there is no name in front of it. */
private fun trailingSegment(stem: String): String? {
    val separator = stem.lastIndexOf('.')
    if (separator <= 0) return null
    return stem.substring(separator + 1).takeIf(String::isNotEmpty)
}

private fun isLanguageTag(segment: String): Boolean =
    LanguageTag.matches(segment) && !isVersionMarker(segment)

private fun isVersionMarker(segment: String): Boolean =
    segment.lowercase(Locale.ROOT) in VersionMarkerSuffixes

private fun isPreferred(candidate: TimedTextMatch, current: TimedTextMatch): Boolean {
    val languageCompare = compareLanguage(candidate.languageTag, current.languageTag)
    if (languageCompare != 0) return languageCompare < 0
    val formatCompare = candidate.candidate.format.ordinal - current.candidate.format.ordinal
    if (formatCompare != 0) return formatCompare < 0
    return candidate.candidate.displayName.lowercase(Locale.ROOT) <
        current.candidate.displayName.lowercase(Locale.ROOT)
}

private fun compareLanguage(left: String?, right: String?): Int {
    val rankCompare = rank(left) - rank(right)
    if (rankCompare != 0) return rankCompare
    return (left ?: "").lowercase(Locale.ROOT).compareTo((right ?: "").lowercase(Locale.ROOT))
}

private fun rank(languageTag: String?): Int = when {
    languageTag == null -> 0
    languageTag.lowercase(Locale.ROOT) in PreferredLanguageTags -> 1
    else -> 2
}
