package com.example.asmrplayer.core.overlay

import com.example.asmrplayer.core.subtitles.TimedTextDocument

/** Independent durable overlay preferences; never affect the in-app lyric renderer. */
data class DesktopLyricsSettings(
    val enabled: Boolean = false,
    val fontSizeSp: Int = 20,
    val lineCount: Int = 2,
    val wordByWord: Boolean = false,
    val textArgb: Int = 0xFFFFFFFF.toInt(),
    val highlightArgb: Int = 0xFF9CD9FF.toInt(),
    val backgroundPercent: Int = 25,
    val locked: Boolean = false,
    val bold: Boolean = true,
    val xPerMille: Int = 500,
    val yPerMille: Int = 200,
) {
    fun clamped() = copy(fontSizeSp = fontSizeSp.coerceIn(12, 36), lineCount = lineCount.coerceIn(1, 4),
        backgroundPercent = backgroundPercent.coerceIn(0, 80),
        xPerMille = xPerMille.coerceIn(0, 1000), yPerMille = yPerMille.coerceIn(0, 1000),
        textArgb = textArgb or 0xFF000000.toInt(), highlightArgb = highlightArgb or 0xFF000000.toInt())
}

internal fun shouldShowDesktopLyrics(enabled: Boolean, permission: Boolean,
    interactive: Boolean, keyguard: Boolean, hasTimedLyrics: Boolean): Boolean =
    enabled && permission && interactive && !keyguard && hasTimedLyrics

/** Keep current centred where possible; intro has upcoming lines but no invented current line. */
internal fun desktopLyricIndices(document: TimedTextDocument, positionMs: Long, count: Int): IntRange {
    if (document.lines.isEmpty()) return IntRange.EMPTY
    val size = count.coerceIn(1, 4).coerceAtMost(document.lines.size)
    val active = document.lineIndexAt(positionMs)
    val start = (active - (size - 1) / 2).coerceIn(0, document.lines.size - size)
    return start until start + size
}
