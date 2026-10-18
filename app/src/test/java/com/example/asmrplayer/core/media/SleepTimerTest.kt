package com.example.asmrplayer.core.media

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SleepTimerTest {
    @Test
    fun `a choice of minutes becomes a deadline on the clock it was given`() {
        assertEquals(1_800_000L, sleepDeadlineFrom(30, nowMs = 0L))
        assertEquals(1_000L + 600_000L, sleepDeadlineFrom(10, nowMs = 1_000L))
    }

    @Test
    fun `zero minutes is not a timer that fires immediately`() {
        assertNull(sleepDeadlineFrom(0, nowMs = 1_000L))
        assertNull(sleepDeadlineFrom(-5, nowMs = 1_000L))
    }

    @Test
    fun `the countdown reads minutes and seconds`() {
        val now = 5_000_000L

        assertEquals("1:00", sleepRemainingLabel(now + 60_000L, now))
        assertEquals("0:42", sleepRemainingLabel(now + 42_000L, now))
        assertEquals("59:59", sleepRemainingLabel(now + 3_599_000L, now))
    }

    @Test
    fun `hours only appear once there is an hour to show`() {
        val now = 0L

        assertEquals("1:00:00", sleepRemainingLabel(3_600_000L, now))
        assertEquals("1:01:05", sleepRemainingLabel(3_665_000L, now))
    }

    @Test
    fun `the countdown rounds up so it never reads zero while the timer is armed`() {
        val now = 0L

        // Half a second left still shows a whole second: 0:00 would say the timer has
        // fired on the same tick the row is still claiming it has not.
        assertEquals("0:01", sleepRemainingLabel(500L, now))
        assertEquals("0:02", sleepRemainingLabel(1_001L, now))
    }

    @Test
    fun `a spent or absent deadline has no countdown`() {
        assertNull(sleepRemainingLabel(null, nowMs = 0L))
        assertNull(sleepRemainingLabel(0L, nowMs = 0L))
        assertNull(sleepRemainingLabel(1_000L, nowMs = 1_000L))
        assertNull(sleepRemainingLabel(1_000L, nowMs = 9_999L))
    }

    @Test
    fun `the offered choices start at off and climb`() {
        assertEquals(0, SleepTimerChoices.first())
        assertEquals(SleepTimerChoices.sorted(), SleepTimerChoices)
        assertEquals(SleepTimerChoices.distinct(), SleepTimerChoices)
        assertNull(sleepDeadlineFrom(SleepTimerChoices.first(), nowMs = 0L))
    }
}
