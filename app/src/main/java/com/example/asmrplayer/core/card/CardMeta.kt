package com.example.asmrplayer.core.card

/**
 * What a card is, beyond what is printed on its face.
 *
 * The card's copy is burned into `text.png` and its title is read back out of
 * `card-config.json`, because the viewer needs both. Everything here is for this app:
 * it is the part of a card the vendored page has never heard of.
 *
 * [mediaId] is what makes "does this song already have a card?" answerable. It is the
 * library item's id -- the document URI's own identity -- and not the title, because a
 * title is free text that two songs may share and a library item may be renamed. A card
 * built before this field existed simply has no id and matches nothing, which is the
 * honest answer: nothing recorded where it came from.
 */
data class CardMeta(
    val mediaId: String? = null,
    /** Pinned cards sort to the top of the warehouse and get a shelf of their own. */
    val pinned: Boolean = false,
    val source: CardSource = CardSource.Local,
    val createdAtMillis: Long = 0L,
) {
    /**
     * Written by hand for the same reason [CardConfig] is: the schema is four keys and
     * only this app reads it, so a JSON library would be a dependency to carry for a
     * file it is cheaper to write correctly once.
     */
    fun toJson(): String = buildString {
        append("{\n")
        append("  \"mediaId\": ${mediaId?.let(::quote) ?: "null"},\n")
        append("  \"pinned\": $pinned,\n")
        append("  \"source\": ${quote(source.name)},\n")
        append("  \"createdAtMillis\": $createdAtMillis\n")
        append("}\n")
    }

    fun withPinned(value: Boolean): CardMeta = copy(pinned = value)

    companion object {
        const val FileName = "meta.json"

        /**
         * Reads a meta file back, and never fails.
         *
         * Every field is optional and every unreadable one falls back to its default, so
         * a truncated or hand-edited file costs the card its pin or its provenance rather
         * than costing it its place in the warehouse. This is the one reader in the
         * package that has to be this forgiving: it is the file a future version will
         * add keys to, and an older build has to keep opening cards that carry them.
         */
        fun parse(text: String): CardMeta {
            val values = HashMap<String, String>()
            text.lineSequence().forEach { line ->
                Entry.matchEntire(line.trim())?.let { match ->
                    values[match.groupValues[1]] = match.groupValues[2]
                }
            }
            return CardMeta(
                mediaId = values["mediaId"]
                    ?.takeIf { it != "null" }
                    ?.let(::unquote)
                    ?.takeIf(String::isNotEmpty),
                pinned = values["pinned"] == "true",
                source = values["source"]
                    ?.let(::unquote)
                    ?.let { name -> CardSource.entries.firstOrNull { it.name == name } }
                    ?: CardSource.Local,
                createdAtMillis = values["createdAtMillis"]?.toLongOrNull() ?: 0L,
            )
        }

        /** `"key": value` or `"key": "value"`, with the trailing comma optional. */
        private val Entry = Regex("""^\s*"([^"]+)"\s*:\s*(.*?)\s*,?\s*$""")

        /**
         * The inverse of [quote], for one line's worth of value.
         *
         * Deliberately narrower than [CardRepository.unescape]: this reads fields this
         * app wrote, and the only escapes it ever writes come from [quote]. A value that
         * is not quoted at all (`null`, `true`, a number) is returned as it stands.
         */
        private fun unquote(value: String): String {
            if (!value.startsWith('"') || !value.endsWith('"') || value.length < 2) return value
            return CardRepository.unescape(value.substring(1, value.length - 1))
        }
    }
}

/**
 * Where a card came from.
 *
 * Only [Local] is produced today: a card is struck from a cover the owner already has.
 * The other two are the extension points the warehouse's own model is shaped around --
 * an imported card arrives as a directory of someone else's layers, and [Model] is the
 * model-generated path the settings switch exists for. Recording the source now costs
 * one line per card and is what lets either arrive later without a migration.
 */
enum class CardSource {
    Local,
    Imported,
    Model,
}
