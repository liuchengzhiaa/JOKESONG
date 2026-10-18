package com.example.asmrplayer.core.overlay

import com.example.asmrplayer.core.subtitles.TimedTextDocument
import com.example.asmrplayer.core.subtitles.TimedTextFormat
import com.example.asmrplayer.core.subtitles.TimedTextLine
import org.junit.Assert.*
import org.junit.Test

class DesktopLyricsTest {
    private val doc = TimedTextDocument(TimedTextFormat.Lrc, (0..5).map { TimedTextLine(it * 1000L, null, "Line $it") })
    @Test fun defaultsAreOffAndTwoLine() {
        val s = DesktopLyricsSettings()
        assertFalse(s.enabled); assertEquals(2, s.lineCount); assertFalse(s.wordByWord)
    }
    @Test fun invalidStoredValuesAreClamped() {
        val s = DesktopLyricsSettings(fontSizeSp = 900, lineCount = -1, backgroundPercent = 100,
            xPerMille = -1, yPerMille = 2000, textArgb = 0).clamped()
        assertEquals(36, s.fontSizeSp); assertEquals(1, s.lineCount); assertEquals(80, s.backgroundPercent)
        assertEquals(0, s.xPerMille); assertEquals(1000, s.yPerMille); assertEquals(0xFF000000.toInt(), s.textArgb)
    }
    @Test fun threeLinesIncludePreviousCurrentNext() { assertEquals(1..3, desktopLyricIndices(doc, 2100, 3)) }
    @Test fun twoLinesShowCurrentAndNext() { assertEquals(2..3, desktopLyricIndices(doc, 2100, 2)) }
    @Test fun singleLineIsCurrent() { assertEquals(2..2, desktopLyricIndices(doc, 2100, 1)) }
    @Test fun introAndEndStayInBounds() {
        assertEquals(0..2, desktopLyricIndices(doc, -1, 3))
        assertEquals(3..5, desktopLyricIndices(doc, 99999, 3))
    }
    @Test fun emptyAndShortDocumentsAreBounded() {
        assertTrue(desktopLyricIndices(doc.copy(lines = emptyList()), 0, 4).isEmpty())
        assertEquals(0..0, desktopLyricIndices(doc.copy(lines = doc.lines.take(1)), 0, 4))
    }
    @Test fun visibleInAppAndOutsideWithPermissionAndTimedLyrics() {
        assertTrue(shouldShowDesktopLyrics(true, true, true, false, true))
        assertFalse(shouldShowDesktopLyrics(false, true, true, false, true))
        assertFalse(shouldShowDesktopLyrics(true, false, true, false, true))
        assertFalse(shouldShowDesktopLyrics(true, true, false, false, true))
        assertFalse(shouldShowDesktopLyrics(true, true, true, true, true))
        assertFalse(shouldShowDesktopLyrics(true, true, true, false, false))
    }
    @Test fun transparentBackgroundSurvivesClamping() {
        assertEquals(0, DesktopLyricsSettings(backgroundPercent = 0).clamped().backgroundPercent)
    }
    @Test fun seekBackRecomputesWindowWithoutStaleFocus() {
        assertEquals(3..5, desktopLyricIndices(doc, 5100, 3))
        assertEquals(0..2, desktopLyricIndices(doc, 1100, 3))
    }
}
