package com.example.asmrplayer.feature.subtitles

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CladdaghOrbitTest {
    @Test
    fun `focal lyric is strongest and neighbours recede`() {
        val focal = claddaghLinePose(0f, PhoneWidth, PhoneHeight)
        val near = claddaghLinePose(1f, PhoneWidth, PhoneHeight)
        val far = claddaghLinePose(6f, PhoneWidth, PhoneHeight)

        assertTrue(focal.scale > near.scale)
        assertTrue(near.scale > far.scale)
        assertTrue(focal.alpha > near.alpha)
        assertTrue(near.alpha > far.alpha)
        assertTrue(focal.blurRadiusDp < near.blurRadiusDp)
        assertTrue(near.blurRadiusDp < far.blurRadiusDp)
    }

    @Test
    fun `past and future occupy different sides of the same tilted loop`() {
        val past = claddaghLinePose(-2f, PhoneWidth, PhoneHeight)
        val future = claddaghLinePose(2f, PhoneWidth, PhoneHeight)

        assertTrue(past.centerX != future.centerX)
        assertTrue(past.centerY != future.centerY)
        assertTrue(past.rotationDegrees != future.rotationDegrees)
    }

    @Test
    fun `geometry is deterministic and bounded for bad viewport sizes`() {
        val first = claddaghLinePose(1.25f, 0f, -20f)
        val second = claddaghLinePose(1.25f, 0f, -20f)

        assertEquals(first, second)
        assertTrue(first.centerX.isFinite())
        assertTrue(first.centerY.isFinite())
        assertTrue(first.rotationDegrees in -42f..42f)
        assertTrue(first.alpha in 0f..1f)
        assertTrue(first.scale > 0f)
    }

    @Test
    fun `portrait and landscape use the shorter side for the physical orbit`() {
        val portrait = claddaghOrbit(1080f, 2400f)
        val landscape = claddaghOrbit(2400f, 1080f)

        assertEquals(portrait.radiusX, landscape.radiusX, Tolerance)
        assertEquals(portrait.radiusY, landscape.radiusY, Tolerance)
    }

    private companion object {
        const val PhoneWidth = 1080f
        const val PhoneHeight = 2400f
        const val Tolerance = 0.0001f
    }
}
