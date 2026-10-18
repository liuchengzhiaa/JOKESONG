package com.example.asmrplayer.ui.card

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The pinch's range has to be the page's range.
 *
 * [CardGesture] drives the vendored card by dispatching synthetic `wheel` events at the
 * page's own unit, which means the two clamps have to be the same two numbers. They
 * cannot be shared -- one is a Kotlin constant and the other is inside a bundle this
 * cannot import -- so the agreement is asserted here instead. If they drift, the pinch
 * stops moving partway with nothing on screen to explain it, which is the kind of bug
 * that gets diagnosed as "the gesture feels wrong" rather than as a number.
 */
class CardGestureTest {

    @Test
    fun `the gesture's magnification range matches the page's wheel clamp`() {
        val clamp = wheelClamp()

        assertEquals(CardGesture.MinZoom, clamp.first, 1e-6)
        assertEquals(CardGesture.MaxZoom, clamp.second, 1e-6)
    }

    @Test
    fun `the clamp is written once, at the wheel listener`() {
        // Two clamps in the bundle would mean the gesture is calibrated against one of
        // them and the page obeys the other.
        val occurrences = bundleText().split("MathUtils.clamp(zoom").size - 1

        assertEquals(1, occurrences)
    }

    @Test
    fun `the script drives the page's own zoom rather than a transform of its own`() {
        val script = CardGesture.Script

        // The wheel event is the whole mechanism: the page's listener is what resizes
        // the camera. A script that set a CSS transform would magnify a raster.
        assertTrue("no wheel event", script.contains("WheelEvent"))
        assertTrue("does not read the live zoom", script.contains("getState"))
        // And it must not stack a second listener on a second viewer.
        assertTrue("no re-entry guard", script.contains("__cardPinch"))
    }

    @Test
    fun `the script is well formed enough to be evaluated`() {
        val script = CardGesture.Script

        assertTrue(script.startsWith("(function ()"))
        assertTrue(script.endsWith("})();"))
        assertEquals(
            "unbalanced parentheses",
            script.count { it == '(' },
            script.count { it == ')' },
        )
        assertTrue("the clamps were not interpolated", script.contains("0.72") && script.contains("2.4"))
    }

    /** The two numbers the bundle clamps `zoom` to, read out of the vendored source. */
    private fun wheelClamp(): Pair<Double, Double> {
        val match = ClampPattern.find(bundleText())
        assertNotNull("the bundle no longer contains a wheel clamp on zoom", match)
        val values = match!!.groupValues.drop(1).map(String::toDouble)
        return values[0] to values[1]
    }

    private fun bundleText(): String {
        val workingDirectory = File(requireNotNull(System.getProperty("user.dir")))
        val candidates = listOf(
            workingDirectory.resolve("src/main/assets/card/app.bundle.js"),
            workingDirectory.resolve("app/src/main/assets/card/app.bundle.js"),
        )
        return requireNotNull(candidates.firstOrNull(File::isFile)) {
            "Card viewer bundle not found from $workingDirectory"
        }.readText()
    }

    private companion object {
        /**
         * The clamp exactly as the library that was vendored writes it. Asserting the
         * shape as well as the numbers means a rewrite of the line -- rather than a
         * retune of it -- fails here and gets looked at.
         */
        val ClampPattern = Regex(
            """MathUtils\.clamp\(\s*zoom\s*-\s*e\.deltaY\s*\*\s*1e-3\s*,\s*""" +
                """(-?[\d.]+)\s*,\s*(-?[\d.]+)\s*\)""",
        )
    }
}
