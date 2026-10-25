package com.example.asmrplayer.core.artwork

import java.io.ByteArrayInputStream
import org.junit.Assert.*
import org.junit.Test

class CoverImageCandidateTest {
    private fun image(name: String, width: Int, height: Int) = CoverImageCandidate(CoverDocument(name, name), width, height)

    @Test fun `pixel comparison uses long arithmetic and beats aspect and file size`() {
        val largest = image("large", 65536, 65536)
        assertEquals(4294967296L, largest.pixels)
        assertEquals(largest, sharperCoverImage(image("small", 65535, 65535), largest))
        assertEquals(largest, sharperCoverImage(largest, null))
        assertEquals(largest, sharperCoverImage(null, largest))
        assertNull(sharperCoverImage(null, null))
    }
    @Test fun `equal areas choose natural name before uri and do not depend on call order`() {
        val first = image("2.png", 1000, 2000); val second = image("10.png", 2000, 1000)
        assertEquals(first, sharperCoverImage(first, second)); assertEquals(first, sharperCoverImage(second, first))
        val sameName = first.copy(document = CoverDocument("zzz", "2.png"))
        assertEquals(first, sharperCoverImage(sameName, first))
    }
    @Test fun `invalid dimensions are rejected`() {
        listOf(0, -1, 65537).forEach { size ->
            try { image("bad", size, 100); fail() } catch (_: IllegalArgumentException) { }
            try { image("bad", 100, size); fail() } catch (_: IllegalArgumentException) { }
        }
    }
    @Test fun `sources above output cap remain probeable but source cap is enforced`() {
        assertTrue(folderCoverLengthAllowed("8388608:42"))
        assertTrue(folderCoverLengthAllowed("8388609:42"))
        assertTrue(folderCoverLengthAllowed("67108864:42"))
        assertFalse(folderCoverLengthAllowed("67108865:42"))
        assertTrue(folderCoverLengthAllowed("0:42"))
        assertTrue(folderCoverLengthAllowed("-1:42"))
        assertTrue(folderCoverLengthAllowed(""))
    }
    @Test fun `single byte and bulk header reads share one strict byte budget`() {
        CoverImageHeaderStream(ByteArrayInputStream(ByteArray(100) { it.toByte() }), 5).use { stream ->
            assertEquals(5, stream.available())
            assertEquals(0, stream.read())
            val buffer = ByteArray(10)
            assertEquals(4, stream.read(buffer, 0, 10)); assertArrayEquals(byteArrayOf(1,2,3,4), buffer.copyOf(4))
            assertEquals(-1, stream.read()); assertEquals(-1, stream.read(buffer))
            assertEquals(0, stream.read(buffer, 0, 0)); assertEquals(0L, stream.skip(100)); assertEquals(0, stream.available())
        }
    }
    @Test fun `skip also consumes header budget and negative skip cannot increase it`() {
        CoverImageHeaderStream(ByteArrayInputStream(ByteArray(100)), 5).use { stream ->
            assertEquals(0L, stream.skip(-5)); assertEquals(4L, stream.skip(4))
            assertEquals(0, stream.read()); assertEquals(-1, stream.read()); assertEquals(0L, stream.skip(1))
        }
    }
    @Test fun `empty streams and zero byte budgets terminate without allocating image buffers`() {
        assertEquals(-1, CoverImageHeaderStream(ByteArrayInputStream(byteArrayOf()), 10).read())
        assertEquals(-1, CoverImageHeaderStream(ByteArrayInputStream(byteArrayOf(1)), 0).read())
    }
    @Test fun `header stream closes its source and cannot reset to bypass the limit`() {
        var closed = false
        val input = object : ByteArrayInputStream(byteArrayOf(1,2)) { override fun close() { closed = true } }
        CoverImageHeaderStream(input, 1).use { stream ->
            assertFalse(stream.markSupported()); stream.mark(10); assertEquals(1, stream.read())
            try { stream.reset(); fail() } catch (_: java.io.IOException) { }
            assertEquals(-1, stream.read())
        }
        assertTrue(closed)
    }
}
