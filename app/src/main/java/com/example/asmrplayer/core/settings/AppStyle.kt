package com.example.asmrplayer.core.settings

/** Accent families shared by Material chrome and every future global control. */
enum class UiAccentPalette {
    Ocean,
    Lavender,
    Amber,
    Mint,
    Rose;

    companion object {
        fun fromOrdinal(ordinal: Int): UiAccentPalette = entries[ordinal.coerceIn(entries.indices)]
    }
}

/**
 * One-tap bundles. Manual colour/motion/size edits switch the visible preset to
 * [Custom] while retaining the independently persisted values.
 */
enum class UiStylePreset(
    val accent: UiAccentPalette,
    val motionPercent: Int,
    val uiScalePercent: Int,
) {
    Balanced(UiAccentPalette.Ocean, motionPercent = 100, uiScalePercent = 100),
    Calm(UiAccentPalette.Lavender, motionPercent = 65, uiScalePercent = 105),
    Compact(UiAccentPalette.Mint, motionPercent = 80, uiScalePercent = 88),
    Vivid(UiAccentPalette.Amber, motionPercent = 100, uiScalePercent = 110),
    Custom(UiAccentPalette.Ocean, motionPercent = 100, uiScalePercent = 100),
    ;

    companion object {
        fun fromOrdinal(ordinal: Int): UiStylePreset = entries[ordinal.coerceIn(entries.indices)]
    }
}
