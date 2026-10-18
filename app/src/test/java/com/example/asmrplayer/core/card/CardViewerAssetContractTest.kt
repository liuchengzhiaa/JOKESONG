package com.example.asmrplayer.core.card

import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardViewerAssetContractTest {

    @Test
    fun embeddedViewerUsesMeasuredWebViewHeightBeforeBuildingScene() {
        val bundle = asset("app.bundle.js")
        val style = asset("style.css")

        val viewportSync = bundle.indexOf("function syncEmbeddedViewport()")
        val initialSync = bundle.indexOf("syncEmbeddedViewport();", startIndex = viewportSync)
        val sceneInit = bundle.indexOf("async function init()")

        assertTrue("embedded viewport sync must exist", viewportSync >= 0)
        assertTrue(
            "viewport must be measured before the Three.js scene is built",
            initialSync >= 0 && sceneInit > initialSync,
        )
        assertTrue("WebView-reported pixels must drive the embedded height", bundle.contains("window.innerHeight"))
        assertTrue(
            "measured height must be published for CSS",
            bundle.contains("style.setProperty(\"--embedded-viewer-height\""),
        )
        assertTrue(
            "embedded stage must consume the measured height rather than svh",
            style.contains("height: var(--embedded-viewer-height, 100vh) !important;"),
        )
        assertTrue(
            "collapsed controls must use a measured pixel limit too",
            style.contains("max-height: var(--embedded-controls-height, 430px);"),
        )
    }

    @Test
    fun embeddedControlsTakeTheirInsetsFromTheHostRatherThanEnv() {
        val style = asset("style.css")

        assertTrue(
            "the host property must be combined with env() rather than replaced by it",
            style.contains("--inset-top: max(var(--safe-top, 0px), env(safe-area-inset-top, 0px));"),
        )
        assertTrue(
            "the top glass row must clear the reported inset, not sit at a constant 14px",
            style.contains("top: calc(var(--inset-top) + 14px);"),
        )
        assertTrue(
            "the workbench must clear the bottom inset",
            style.contains("bottom: max(78px, calc(var(--inset-bottom) + 68px));"),
        )

        // Android System WebView in an edge-to-edge app answers `env(safe-area-inset-*)`
        // with zero, so a rule that positions from it alone sits under the status bar.
        // It may only appear as the second argument of the four derived properties.
        val envLines = style.lineSequence().filter { it.contains("env(safe-area-inset") }.toList()
        assertEquals("env() may only be a fallback for the host property", 4, envLines.size)
        assertTrue(envLines.all { it.trimStart().startsWith("--inset-") })
    }

    @Test
    fun bareModeHidesTheShellChromeBeforeTheFirstFrame() {
        val html = asset("index.html")
        val style = asset("style.css")

        // `bare` is the card shown as a cover rather than as a page about itself. The
        // class has to be set by the inline bootstrap, before the stylesheet is parsed:
        // set later it would paint the save button and the depth panel for one frame,
        // over the artwork they are about.
        val bootstrap = html.indexOf("app-bare")
        val stylesheet = html.indexOf("rel=\"stylesheet\"")
        assertTrue("no bare bootstrap", bootstrap >= 0)
        assertTrue("no stylesheet link", stylesheet >= 0)
        assertTrue(
            "the bare class must be set before the stylesheet is linked",
            bootstrap < stylesheet,
        )

        // And the stylesheet has to actually hide the chrome, rather than the class
        // existing and doing nothing.
        val rule = style.substringAfter(".app-bare", missingDelimiterValue = "")
        assertTrue("no .app-bare rule", rule.isNotEmpty())
        listOf(".masthead", ".header-actions", ".viewer-controls-toggle", ".artwork-bar")
            .forEach { selector ->
                assertTrue("$selector is not hidden in bare mode", rule.contains(selector))
            }
    }

    private fun asset(name: String): String {
        val workingDirectory = File(requireNotNull(System.getProperty("user.dir")))
        val candidates = listOf(
            workingDirectory.resolve("src/main/assets/card/$name"),
            workingDirectory.resolve("app/src/main/assets/card/$name"),
        )
        return requireNotNull(candidates.firstOrNull(File::isFile)) {
            "Card viewer asset not found: $name from $workingDirectory"
        }.readText()
    }
}
