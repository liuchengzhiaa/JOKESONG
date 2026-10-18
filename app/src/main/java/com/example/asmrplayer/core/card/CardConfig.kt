package com.example.asmrplayer.core.card

import com.example.asmrplayer.core.model.LibraryItem
import kotlin.math.abs

/**
 * The seven pieces of copy a card carries.
 *
 * Six of them are burned into `text.png` and therefore onto the card face itself
 * (title, subtitle, collection, tagline, technique, edition -- the same six slots the
 * skill's `generate_typography.py` lays out). The seventh, [description], is read by
 * the viewer's own shell and its about-dialog, so it never reaches the card face and
 * is the one field whose length costs nothing.
 *
 * A blank field is legal everywhere: the typography pass draws nothing where the text
 * is empty, and the viewer's shell assigns an empty string.
 */
data class CardTextFields(
    val title: String = "",
    val subtitle: String = "",
    val collection: String = "",
    val tagline: String = "",
    val technique: String = "",
    val edition: String = DefaultEdition,
    val description: String = "",
) {
    /** Only whitespace-trimmed: a title is allowed to be one character. */
    fun trimmed(): CardTextFields = copy(
        title = title.trim(),
        subtitle = subtitle.trim(),
        collection = collection.trim(),
        tagline = tagline.trim(),
        technique = technique.trim(),
        edition = edition.trim(),
        description = description.trim(),
    )

    companion object {
        const val DefaultEdition = "001 / 001"
        const val DefaultCollection = "个人全息典藏"

        /**
         * Starts a new card from a library item.
         *
         * Only two of the seven can honestly come from the item: its title, and its
         * subtitle line, which is the artist for an audio file. The library model
         * carries no album, so [collection] falls back to the same house default the
         * reference cards used rather than to a wrong guess.
         */
        fun fromItem(item: LibraryItem): CardTextFields = CardTextFields(
            title = item.title,
            subtitle = item.subtitle,
            collection = DefaultCollection,
            edition = DefaultEdition,
            description = listOf(item.subtitle, item.title)
                .filter { it.isNotBlank() }
                .joinToString(" · "),
        )
    }
}

/**
 * Everything the vendored viewer needs, in the shape its own `card-config.json` uses.
 *
 * Only the keys the viewer actually reads are written. That set was read out of the
 * bundle rather than from the skill's sample file, because the sample is a superset:
 * `sourceMode` and `artworkFit` are absent here on purpose, since both only matter for
 * the relief variant and for source artwork that is not already card-shaped. Our layers
 * are generated at exactly the card face's 1024x1536, so the viewer's own `[1, 1]`
 * default is the correct fit and writing anything else would scale a correct layer.
 *
 * Asset paths stay relative (`./assets/...`) because the viewer resolves them against
 * the document, and every card is served from its own directory.
 */
data class CardConfig(
    val fields: CardTextFields,
    val preset: CardStylePreset = CardStylePreset.Default,
) {
    fun toJson(): String = buildString {
        append("{\n")
        appendEntry("title", fields.title)
        appendEntry("subtitle", fields.subtitle)
        appendEntry("collection", fields.collection)
        appendEntry("tagline", fields.tagline)
        appendEntry("technique", fields.technique)
        appendEntry("edition", fields.edition)
        appendEntry("description", fields.description)
        append("  \"assets\": {\n")
        appendEntry("model", ModelPath, indent = "    ")
        appendEntry("subject", SubjectPath, indent = "    ")
        appendEntry("background", BackgroundPath, indent = "    ")
        // `text` is the last entry, so it carries no trailing comma.
        append("    \"text\": ${quote(TextPath)}\n")
        append("  },\n")
        append("  \"parameters\": {\n")
        appendNumberEntry("subjectScale", preset.subjectScale)
        appendNumberEntry("subjectDepth", preset.subjectDepth)
        appendNumberEntry("backgroundDepth", preset.backgroundDepth)
        // `foil` is the last entry, so it carries no trailing comma.
        append("    \"foil\": ${number(preset.foil)}\n")
        append("  },\n")
        append("  \"safeArea\": {\n")
        append("    \"scale\": ${number(preset.safeAreaScale)},\n")
        append(
            "    \"offset\": [${number(preset.safeAreaOffsetX)}, " +
                "${number(preset.safeAreaOffsetY)}]\n",
        )
        append("  },\n")
        append("  \"appearance\": {\n")
        // The shell's own background stays the viewer's default: it is the page behind
        // the card, not part of the card, and the reference cards all shipped light.
        append("    \"finish\": ${quote(preset.finish)}\n")
        append("  }\n")
        append("}\n")
    }

    private fun StringBuilder.appendEntry(key: String, value: String, indent: String = "  ") {
        append("$indent${quote(key)}: ${quote(value)},\n")
    }

    private fun StringBuilder.appendNumberEntry(key: String, value: Float, indent: String = "    ") {
        append("$indent${quote(key)}: ${number(value)},\n")
    }

    companion object {
        const val ModelPath = "./assets/card.glb"
        const val SubjectPath = "./assets/subject.png"
        const val BackgroundPath = "./assets/background.png"
        const val TextPath = "./assets/text.png"

        /** The viewer fetches exactly this name, relative to the document. */
        const val ConfigFileName = "card-config.json"
    }
}

/**
 * A JSON string literal.
 *
 * Hand-writing the document is only safe because this is total: every character that
 * could end the literal, or that JSON requires escaped, is escaped, and every other
 * control character -- which JSON forbids raw but which a title pasted from anywhere
 * could contain -- goes out as `\u`. Card copy is free text typed by hand, so this is
 * the difference between a working card and a config the viewer cannot parse.
 */
internal fun quote(value: String): String = buildString {
    append('"')
    for (character in value) {
        when (character) {
            '"' -> append("\\\"")
            '\\' -> append("\\\\")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            '\b' -> append("\\b")
            '\u000C' -> append("\\f")
            else -> if (character < ' ') {
                append("\\u").append(character.code.toString(16).padStart(4, '0'))
            } else {
                append(character)
            }
        }
    }
    append('"')
}

/**
 * A JSON number, at the two decimal places the viewer's sliders step in.
 *
 * The sliders step by 0.01, so a longer fraction would only make the file harder to
 * read while the value the viewer shows is the same.
 */
internal fun number(value: Float): String {
    val rounded = Math.round(value * 100f) / 100f
    // A whole number is written as one, so an integer-valued parameter does not read
    // as though it carried a measured precision it does not have.
    if (abs(rounded - Math.round(rounded)) < 0.0001f) return Math.round(rounded).toString()
    return rounded.toString()
}
