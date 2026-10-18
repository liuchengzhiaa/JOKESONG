package com.example.asmrplayer.core.card

import android.content.Context
import java.io.File
import java.util.UUID

/** One card on disk, as the warehouse needs to show it. */
data class CardSummary(
    val id: String,
    /** The card's own title, read back out of its config; the id if that cannot be read. */
    val title: String,
    /**
     * The series the card was filed under, which is the `collection` line on its face.
     *
     * Read from the config rather than stored again in `meta.json`: it is the same value
     * the card itself shows, and a second copy would be a second thing to keep in step.
     */
    val series: String,
    val directory: File,
    val createdAtMillis: Long,
    val meta: CardMeta = CardMeta(),
) {
    /** The screenshot the viewer's own save produced. Null until the card has been saved. */
    val screenshot: File? get() = File(directory, CardRepository.ScreenshotName).takeIf(File::isFile)

    /**
     * The flat picture of the card face written when it was built.
     *
     * A card used to have no picture of itself until the owner opened it and pressed
     * save, so a card that had just been made appeared in the warehouse as an empty
     * frame -- which reads as a card that failed, not as one that has not been saved.
     * The build writes this from the same three layers the viewer loads, so a new card
     * has a face from the moment it exists.
     */
    val preview: File? get() = File(directory, CardRepository.PreviewName).takeIf(File::isFile)

    /** What to draw in the warehouse: the real capture if there is one, else the preview. */
    val thumbnail: File? get() = screenshot ?: preview

    val configFile: File get() = File(directory, CardConfig.ConfigFileName)

    val metaFile: File get() = File(directory, CardMeta.FileName)

    val pinned: Boolean get() = meta.pinned
}

/**
 * Cards live as plain directories under the app's private files, one per card.
 *
 * Not in Room: a new entity would take the schema from 1 to 2 and the project has no
 * migration mechanism, while a card is a handful of files that are only ever read
 * whole. It also makes the safety property trivial to hold -- every path this class
 * touches is checked to be inside [root] first, so deleting a card can never reach
 * user media, which only ever lives in a document provider.
 *
 * Each card owns a directory because the viewer's asset names are fixed
 * (`./assets/subject.png` and friends). Serving two cards from one origin path would
 * let the WebView's cache answer card B's texture request with card A's image, so the
 * directory is also the unit of address, not just of storage.
 */
class CardRepository(context: Context) {
    private val root = File(context.applicationContext.filesDir, RootDirectoryName)

    /**
     * Every card, newest first.
     *
     * Two small files are read per card rather than a database being consulted, which is
     * the trade this storage choice already made: a card is a directory, and listing them
     * is listing a directory.
     */
    fun listCards(): List<CardSummary> = root.listFiles()
        .orEmpty()
        .filter { it.isDirectory && it.safeId() != null }
        .map { directory ->
            val config = File(directory, CardConfig.ConfigFileName)
            val meta = readMeta(File(directory, CardMeta.FileName))
            CardSummary(
                id = directory.name,
                title = readValue(config, TitleKey) ?: directory.name,
                series = readValue(config, CollectionKey).orEmpty(),
                directory = directory,
                // The directory's own timestamp is the fallback for a card built before
                // meta.json existed; a card with a recorded time keeps the time it was
                // actually struck rather than when its files were last touched.
                createdAtMillis = meta.createdAtMillis.takeIf { it > 0L } ?: directory.lastModified(),
                meta = meta,
            )
        }
        .sortedByDescending(CardSummary::createdAtMillis)

    /**
     * The card struck from one library item, or null.
     *
     * Newest first, so a song that has been carded twice opens the newer one; the older
     * is still in the warehouse and can be pinned to say which is the one that counts.
     */
    fun findForMedia(mediaId: String): CardSummary? {
        if (mediaId.isEmpty()) return null
        return listCards().firstOrNull { it.meta.mediaId == mediaId }
    }

    /** Creates the card's directory and its empty `assets/`, and returns the former. */
    fun createCard(): File {
        val directory = File(root, newCardId())
        File(directory, AssetsDirectoryName).mkdirs()
        return directory
    }

    /** Records the card's own bookkeeping, once its layers are on disk. */
    fun writeMeta(cardDirectory: File, meta: CardMeta) {
        File(cardDirectory, CardMeta.FileName).writeText(meta.toJson())
    }

    /**
     * Pins or unpins one card.
     *
     * The pin is the only thing about a card the owner changes after it is made, and it
     * is the one write this class does to a card it did not just create.
     */
    fun setPinned(cardId: String, pinned: Boolean): Boolean {
        val directory = cardDirectoryOrNull(cardId) ?: return false
        val metaFile = File(directory, CardMeta.FileName)
        val current = readMeta(metaFile).withPinned(pinned)
        return runCatching { metaFile.writeText(current.toJson()) }.isSuccess
    }

    /**
     * Removes one card, and only one card.
     *
     * [cardId] is validated before it is joined, so a name carrying a separator or a
     * parent reference cannot escape [root]; a rejected id deletes nothing.
     */
    fun deleteCard(cardId: String): Boolean {
        val directory = cardDirectoryOrNull(cardId) ?: return false
        return directory.deleteRecursively()
    }

    /**
     * Removes several cards, and reports how many went.
     *
     * Each id goes through the same single-card path rather than this method joining
     * anything itself, so a batch can never delete something a single delete would have
     * refused.
     */
    fun deleteCards(cardIds: Collection<String>): Int =
        cardIds.count { deleteCard(it) }

    fun cardDirectoryOrNull(cardId: String): File? {
        if (!isSafeSegment(cardId)) return null
        val directory = File(root, cardId)
        return directory.takeIf { it.isDirectory }
    }

    private fun File.safeId(): String? = name.takeIf(::isSafeSegment)

    companion object {
        const val RootDirectoryName = "cards"
        const val AssetsDirectoryName = "assets"
        const val ScreenshotName = "card.png"
        const val PreviewName = "preview.png"

        /** The two config keys the warehouse reads without parsing the whole document. */
        private const val TitleKey = "title"
        private const val CollectionKey = "collection"

        /**
         * Ids are opaque and sortable-ish rather than derived from the title: a title
         * is free text that may be empty, repeated, or contain a path separator, and
         * none of those may decide where a file lands.
         */
        fun newCardId(): String = "${System.currentTimeMillis()}-${UUID.randomUUID()}"

        /**
         * A single path segment and nothing else.
         *
         * Rejecting on the characters that make a name a path is what keeps every
         * directory operation inside the cards root; `.` and `..` are rejected as
         * whole names because both resolve somewhere other than a card.
         */
        fun isSafeSegment(name: String): Boolean =
            name.isNotEmpty() &&
                name != "." &&
                name != ".." &&
                name.none { it == '/' || it == '\\' || it == '\u0000' }

        /**
         * Reads the `"title"` value out of a generated config.
         *
         * A full JSON parse would be a parser to maintain and test for one string; the
         * writer above is the only producer of these files and writes exactly this
         * shape, so the reader is written to that shape and returns null for anything
         * else. Null is not an error path -- the warehouse falls back to the card id.
         */
        fun readTitle(configFile: File): String? = readValue(configFile, TitleKey)

        /**
         * One top-level string value out of a config, or null.
         *
         * Matched per line against the exact shape [CardConfig] writes, which is what
         * keeps this from needing to understand nesting: the keys it is asked for are
         * top-level strings, and a nested key of the same name (`assets` has none) would
         * still be returned with its own indentation stripped the same way.
         */
        fun readValue(configFile: File, key: String): String? = runCatching {
            val pattern = Regex("""^\s*"${Regex.escape(key)}"\s*:\s*"(.*)",\s*$""")
            configFile.useLines { lines ->
                lines.firstNotNullOfOrNull { line ->
                    pattern.matchEntire(line.trim())?.groupValues?.get(1)?.let(::unescape)
                }
            }
        }.getOrNull()

        /** A meta file, or the defaults for a card that has none. */
        fun readMeta(metaFile: File): CardMeta = runCatching {
            if (!metaFile.isFile) return@runCatching CardMeta()
            CardMeta.parse(metaFile.readText())
        }.getOrDefault(CardMeta())

        /** The inverse of [quote], for the escapes that can appear in a single-line value. */
        internal fun unescape(value: String): String {
            if (!value.contains('\\')) return value
            val out = StringBuilder(value.length)
            var index = 0
            while (index < value.length) {
                val character = value[index]
                if (character != '\\' || index == value.length - 1) {
                    out.append(character)
                    index++
                    continue
                }
                when (val escape = value[index + 1]) {
                    '"' -> out.append('"')
                    '\\' -> out.append('\\')
                    'n' -> out.append('\n')
                    'r' -> out.append('\r')
                    't' -> out.append('\t')
                    'b' -> out.append('\b')
                    'f' -> out.append('\u000C')
                    'u' -> {
                        val hex = value.substring(index + 2, minOf(index + 6, value.length))
                        val code = hex.toIntOrNull(16)
                        if (code == null || hex.length < 4) {
                            out.append(escape)
                            index += 2
                            continue
                        }
                        out.append(code.toChar())
                        index += 6
                        continue
                    }
                    else -> out.append(escape)
                }
                index += 2
            }
            return out.toString()
        }
    }
}
