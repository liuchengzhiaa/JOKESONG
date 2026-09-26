package com.example.asmrplayer.feature.subtitles

import com.example.asmrplayer.core.subtitles.TimedTextDocument

/**
 * The timed text of the track whose subtitle detail is open.
 *
 * [itemId] travels with the document so a surface can tell "these are this track's
 * lyrics" from "these are the lyrics of the track that was playing a moment ago":
 * the queue can advance while the detail stays open, and the old lines must not be
 * shown as if they belonged to the new track.
 */
data class SubtitleDetailState(
    val itemId: String? = null,
    val isLoading: Boolean = false,
    /**
     * The name of the file that was matched, or null when nothing matched. Shown to
     * the reader, because "which of my two lyric files did it pick" is a question the
     * screen can answer and nothing else can.
     */
    val sourceName: String? = null,
    /** The language suffix that file carried, when it had one. */
    val languageTag: String? = null,
    /** Null when no file matched, or when the matched file could not be read. */
    val document: TimedTextDocument? = null,
) {
    /** True when this state describes [itemId] rather than the previous track. */
    fun describes(itemId: String?): Boolean = itemId != null && this.itemId == itemId

    companion object {
        fun unmatched(itemId: String) = SubtitleDetailState(itemId = itemId)
    }
}

/**
 * What the subtitle surface can actually show.
 *
 * Five outcomes rather than two, because the interesting cases are the ones between
 * "there are lyrics" and "there are none", and each of them deserves its own sentence
 * instead of a blank list. Nothing here invents a timeline: a file without timestamps
 * is reported as untimed rather than given evenly spaced times, which is the whole
 * point of keeping [TimedTextDocument.untimedText] apart.
 */
sealed interface LyricsView {
    /** Nothing decided yet. */
    data object Loading : LyricsView

    /**
     * Nothing to show. [sourceName] separates the two reasons: null means no file
     * beside this track matched it, and a name means that file was found but could not
     * be read -- a revoked grant, or a provider that refused the stream.
     */
    data class NoFile(val sourceName: String?) : LyricsView

    /** A file matched and parsed, but there is nothing in it to show. */
    data class EmptyFile(val sourceName: String?) : LyricsView

    /** A timed file: the lines follow playback. */
    data class Timed(val document: TimedTextDocument) : LyricsView

    /** A file with words but no timestamps; it is shown and admitted to be static. */
    data class Untimed(val text: List<String>) : LyricsView
}

fun SubtitleDetailState.lyricsView(): LyricsView {
    val document = document
    return when {
        isLoading -> LyricsView.Loading
        document == null -> LyricsView.NoFile(sourceName)
        document.isTimed -> LyricsView.Timed(document)
        document.untimedText.isNotEmpty() -> LyricsView.Untimed(document.untimedText)
        else -> LyricsView.EmptyFile(sourceName)
    }
}
