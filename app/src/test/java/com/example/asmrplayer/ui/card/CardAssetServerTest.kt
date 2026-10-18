package com.example.asmrplayer.ui.card

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The card's address, which is the one thing the host and the page have to agree on.
 *
 * A card is fetched at its own path because the WebView's cache would otherwise answer
 * one card's texture request with another's. The two query parameters are what select
 * the shell: `embedded` for the Android frame, `bare` for the player's artwork slot,
 * where the shell's own controls would be drawn over the cover they are about.
 */
class CardAssetServerTest {

    @Test
    fun `a card is addressed at its own path`() {
        val url = CardAssetServer.viewerUrl("1727000000000-abc")

        assertEquals(
            "https://appassets.androidplatform.net/cards/1727000000000-abc/index.html?embedded=1",
            url,
        )
    }

    @Test
    fun `bare adds its parameter without dropping embedded`() {
        val url = CardAssetServer.viewerUrl("card", bare = true)

        assertTrue("embedded was dropped", url.contains("embedded=1"))
        assertTrue("bare was not added", url.contains("${CardAssetServer.BareParam}=1"))
    }

    @Test
    fun `an ordinary card never asks for bare`() {
        // The parameter is read by `index.html` before CSS parses, so a url that carries
        // it by accident is a card with no save button and no panel, and no way to tell
        // from the card itself why.
        assertFalse(CardAssetServer.viewerUrl("card").contains(CardAssetServer.BareParam))
    }

    @Test
    fun `the two hosts the same id serves are different urls`() {
        // If they were the same, opening the card as a cover and then as a page would
        // show whichever one was loaded first.
        assertTrue(CardAssetServer.viewerUrl("card", bare = true) != CardAssetServer.viewerUrl("card"))
    }
}
