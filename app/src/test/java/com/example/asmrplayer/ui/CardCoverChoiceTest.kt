package com.example.asmrplayer.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Which face the player wears, as arithmetic.
 *
 * The rule is three values folded into one, and the interesting cases are the two the
 * owner named: a card may only show when the song has one, and the setting is a default
 * rather than a rule, so a choice -- once made -- outranks it. The state is transient and
 * keyed to the song, which is what keeps "showed the card" from meaning "always shows the
 * card"; these cases pin the fold, and the keying lives in the composable.
 */
class CardCoverChoiceTest {

    @Test
    fun aSongWithoutACardNeverShowsOne() {
        assertFalse(showsCardCover(hasCard = false, choice = null, defaultToCard = false))
        // Even with the setting on, and even if something had left a choice behind: the
        // card is the thing being shown, and there is no card.
        assertFalse(showsCardCover(hasCard = false, choice = null, defaultToCard = true))
        assertFalse(showsCardCover(hasCard = false, choice = true, defaultToCard = true))
    }

    @Test
    fun theSettingIsTheAnswerUntilTheOwnerGivesOne() {
        assertFalse(showsCardCover(hasCard = true, choice = null, defaultToCard = false))
        assertTrue(showsCardCover(hasCard = true, choice = null, defaultToCard = true))
    }

    @Test
    fun theOwnersChoiceOutranksTheSettingBothWays() {
        assertTrue(showsCardCover(hasCard = true, choice = true, defaultToCard = false))
        assertFalse(showsCardCover(hasCard = true, choice = false, defaultToCard = true))
    }
}
