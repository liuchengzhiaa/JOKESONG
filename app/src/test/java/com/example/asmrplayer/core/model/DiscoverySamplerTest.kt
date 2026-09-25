package com.example.asmrplayer.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DiscoverySamplerTest {
    @Test
    fun `sample is stable for the same seed`() {
        val items = (1..40).toList()

        assertEquals(
            DiscoverySampler.sample(items, requestedCount = 15, seed = 42),
            DiscoverySampler.sample(items, requestedCount = 15, seed = 42),
        )
    }

    @Test
    fun `sample keeps the playing item without exceeding requested size`() {
        val result = DiscoverySampler.sample(
            items = (1..40).toList(),
            requestedCount = 10,
            seed = 9,
            pinned = 37,
        )

        assertEquals(10, result.size)
        assertTrue(37 in result)
        assertEquals(result.size, result.distinct().size)
    }
}
