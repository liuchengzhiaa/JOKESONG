package com.example.asmrplayer.core.settings

/**
 * Presentation of the collapsible tool cluster -- the corner button and the panel
 * that unfolds out of it.
 *
 * One setting drives every surface that hosts one, because the cluster is meant to
 * read as the same affordance wherever it appears; styling the wall's copy and the
 * player's copy separately would make them two controls that happen to share a shape.
 *
 * [Frosted] is first and is exactly what shipped before this setting existed, so an
 * install that predates it renders unchanged and a fresh one matches the wall.
 */
enum class ToolClusterStyle {
    /** Translucent disc, hairline white edge, a soft top-lit gradient and a shadow. */
    Frosted,

    /** No fill, a heavier accent ring. Reads as a drawn control rather than a pane. */
    Outline,

    /** An opaque accent disc. The most legible over busy artwork, the least glassy. */
    Solid,

    /** The lightest backing that still keeps the glyph legible over artwork. */
    Minimal,
    ;

    companion object {
        val Default = Frosted

        fun fromOrdinal(ordinal: Int): ToolClusterStyle = entries[ordinal.coerceIn(entries.indices)]
    }
}
