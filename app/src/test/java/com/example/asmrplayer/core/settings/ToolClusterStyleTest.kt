package com.example.asmrplayer.core.settings

import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test

/**
 * One setting drives every collapsible tool cluster in the app, and it is stored as a
 * bare ordinal. That makes ordinal zero load-bearing: it is what an install that has
 * never seen this setting reads back, so it has to be the look that shipped before the
 * setting existed.
 */
class ToolClusterStyleTest {

    @Test
    fun `an install that has never chosen a style gets the original one`() {
        assertSame(ToolClusterStyle.Frosted, ToolClusterStyle.Default)
        assertSame(ToolClusterStyle.Frosted, ToolClusterStyle.entries.first())
        assertEquals(0, ToolClusterStyle.Default.ordinal)
    }

    @Test
    fun `an ordinal from storage is clamped rather than trusted`() {
        assertSame(ToolClusterStyle.Frosted, ToolClusterStyle.fromOrdinal(-1))
        assertSame(ToolClusterStyle.Frosted, ToolClusterStyle.fromOrdinal(Int.MIN_VALUE))
        assertSame(ToolClusterStyle.entries.last(), ToolClusterStyle.fromOrdinal(Int.MAX_VALUE))
    }

    @Test
    fun `an ordinal that is in range is itself`() {
        for (style in ToolClusterStyle.entries) {
            assertSame(style, ToolClusterStyle.fromOrdinal(style.ordinal))
        }
    }
}
