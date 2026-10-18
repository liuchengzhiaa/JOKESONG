package com.example.asmrplayer.ui.card

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The host's half of the safe-area contract.
 *
 * The page draws its own glass actions and workbench, so the insets those are laid out
 * from are the one piece of this screen's layout that Compose cannot be given. What is
 * asserted here is that the script which hands them over is a single self-contained
 * expression, that it publishes exactly the four properties the stylesheet reads and
 * nothing else, and that it survives the window between navigating and parsing, when
 * there is no document element to write to.
 */
class CardViewerSafeAreaTest {

    @Test
    fun publishesEveryInsetTheStylesheetReads() {
        val script = safeAreaScript(top = 46, right = 4, bottom = 24, left = 0)

        assertEquals(
            setOf("--safe-top", "--safe-right", "--safe-bottom", "--safe-left"),
            properties(script),
        )
        assertEquals("46px", value(script, "--safe-top"))
        assertEquals("4px", value(script, "--safe-right"))
        assertEquals("24px", value(script, "--safe-bottom"))
        assertEquals("0px", value(script, "--safe-left"))
    }

    @Test
    fun isOneSelfContainedExpression() {
        val script = safeAreaScript(0, 0, 0, 0)

        assertTrue(script.startsWith("(function(){"))
        assertTrue(script.endsWith("})()"))
        // One line, because it is handed to `evaluateJavascript` as a bare expression
        // rather than wrapped by the caller.
        assertFalse(script.contains("\n"))
    }

    @Test
    fun guardsAgainstAPageThatHasNotParsedYet() {
        val script = safeAreaScript(0, 0, 0, 0)

        assertTrue(script.contains("document.documentElement"))
        // `evaluateJavascript` can land after the navigation but before the first parse,
        // and `documentElement` is null for exactly that long.
        assertTrue(script.contains("if(!root)return;"))
    }

    /** Every `setProperty` name in the script, which is the set the page will have. */
    private fun properties(script: String): Set<String> =
        Regex("""setProperty\('([^']+)'""").findAll(script).map { it.groupValues[1] }.toSet()

    private fun value(script: String, property: String): String = requireNotNull(
        Regex("""setProperty\('${Regex.escape(property)}','([^']*)'\)""").find(script),
    ) { "no value published for $property" }.groupValues[1]
}
