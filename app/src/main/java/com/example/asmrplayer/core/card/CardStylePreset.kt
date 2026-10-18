package com.example.asmrplayer.core.card

/**
 * The material a card is struck in.
 *
 * A preset is the whole starting look, not just a colour: the viewer's four finishes
 * (`pearl` / `silver` / `gold` / `original`) differ in how the foil sweep is tinted,
 * and each is paired here with the amount of foil it wants and the camera geometry
 * that reads best under it. Both halves are written into the generated
 * `card-config.json`, so a card opens in the studio already wearing its material
 * rather than at the viewer's own defaults.
 *
 * The geometry is the reference card's. Every preset shares it deliberately: changing
 * depth as well as finish would make two cards of the same cover look like different
 * scenes, and the material is what the owner is choosing between.
 *
 * Every value sits inside the range of the viewer's own sliders
 * (`index.html`: scale 0.95-1.35, depth -0.45-0.45, bg-depth -0.4-0.4, foil 0-1), so a
 * composed card can still be adjusted by hand without the slider clamping it back.
 */
enum class CardStylePreset(
    /** The viewer's `appearance.finish`; also the `data-finish` name it uses. */
    val finish: String,
    val foil: Float,
    val subjectScale: Float,
    val subjectDepth: Float,
    val backgroundDepth: Float,
    val safeAreaScale: Float,
    val safeAreaOffsetX: Float,
    val safeAreaOffsetY: Float,
) {
    /** The reference card's own numbers, in gold. What the skill's best output used. */
    Heirloom(
        finish = "gold",
        foil = 0.68f,
        subjectScale = 1.18f,
        subjectDepth = 0.42f,
        backgroundDepth = -0.28f,
        safeAreaScale = 1.08f,
        safeAreaOffsetX = 0.0f,
        safeAreaOffsetY = -0.035f,
    ),

    /** The viewer's own default finish, so this is what a bare config looks like. */
    Pearl(
        finish = "pearl",
        foil = 0.52f,
        subjectScale = 1.18f,
        subjectDepth = 0.42f,
        backgroundDepth = -0.28f,
        safeAreaScale = 1.08f,
        safeAreaOffsetX = 0.0f,
        safeAreaOffsetY = -0.035f,
    ),

    Silver(
        finish = "silver",
        foil = 0.60f,
        subjectScale = 1.18f,
        subjectDepth = 0.42f,
        backgroundDepth = -0.28f,
        safeAreaScale = 1.08f,
        safeAreaOffsetX = 0.0f,
        safeAreaOffsetY = -0.035f,
    ),

    /**
     * No foil at all. The cover's own colours are the card, which is the only preset
     * that cannot mislead about what the artwork actually looks like.
     */
    Original(
        finish = "original",
        foil = 0.0f,
        subjectScale = 1.18f,
        subjectDepth = 0.42f,
        backgroundDepth = -0.28f,
        safeAreaScale = 1.08f,
        safeAreaOffsetX = 0.0f,
        safeAreaOffsetY = -0.035f,
    ),
    ;

    companion object {
        val Default = Heirloom

        fun fromOrdinal(ordinal: Int): CardStylePreset = entries[ordinal.coerceIn(entries.indices)]

        /** Resolves a stored `finish` name back to its preset, for editing an old card. */
        fun fromFinish(finish: String?): CardStylePreset =
            entries.firstOrNull { it.finish == finish } ?: Default
    }
}
