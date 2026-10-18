package com.example.asmrplayer.ui

import com.example.asmrplayer.core.media.SleepTimerChoices
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The timer page's wording is a list in the UI and a list of durations in the core,
 * and the two have to agree.
 *
 * This is the test that would catch a duration being added to [SleepTimerChoices] and
 * then drawn with whatever label the `when` happened to fall through to.
 *
 * The assertions stop at "this duration has a label", and deliberately do not compare
 * one label against another: a plain JVM unit test has no resource table, so every
 * `R.string.*` is zero here and any two of them compare equal. Which wording each row
 * carries is a thing to read on the phone, not to assert here.
 */
class SleepChoiceLabelTest {

    @Test
    fun `every offered duration has its own wording`() {
        val unlabelled = SleepTimerChoices.filter { sleepChoiceLabel(it) == null }

        assertTrue("durations with no label: $unlabelled", unlabelled.isEmpty())
    }

    @Test
    fun `turning the timer off is a row like any other`() {
        assertNotNull(sleepChoiceLabel(0))
    }

    @Test
    fun `a duration the app does not offer has no wording`() {
        assertNull(sleepChoiceLabel(7))
        assertNull(sleepChoiceLabel(-5))
    }
}
