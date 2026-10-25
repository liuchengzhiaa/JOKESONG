package com.example.asmrplayer.core.artwork

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.concurrent.CancellationException
import org.junit.Assert.*
import org.junit.Test

class CoverImagePolicyTest {
    @Test fun `sampling bounds wide tall tiny and extreme aspect without upscaling`() {
        assertEquals(CoverPixelSize(2048, 1536), sampledCoverSize(2400, 1800))
        assertEquals(CoverPixelSize(1536, 2048), sampledCoverSize(1800, 2400))
        assertEquals(CoverPixelSize(2048, 1), sampledCoverSize(65536, 1))
        assertEquals(CoverPixelSize(1, 2048), sampledCoverSize(1, 65536))
        assertEquals(CoverPixelSize(64, 32), sampledCoverSize(64, 32))
        assertEquals(CoverPixelSize(512, 512), sampledCoverSize(65536, 65536, 512))
    }
    @Test fun `bad dimensions and edges are rejected`() {
        for (bad in listOf(0, -1, 65537)) {
            try { sampledCoverSize(bad, 100); fail() } catch (_: IllegalArgumentException) { }
            try { sampledCoverSize(100, bad); fail() } catch (_: IllegalArgumentException) { }
        }
        try { sampledCoverSize(100, 100, 0); fail() } catch (_: IllegalArgumentException) { }
    }
    @Test fun `only compliant bytes and dimensions can retain original encoding`() {
        assertTrue(canCopyCoverUnchanged(MaximumFolderCoverBytes, 2048, 2048))
        assertFalse(canCopyCoverUnchanged(MaximumFolderCoverBytes + 1, 100, 100))
        assertFalse(canCopyCoverUnchanged(100, 2049, 100))
        assertFalse(canCopyCoverUnchanged(100, 100, 2049))
        assertFalse(canCopyCoverUnchanged(0, 100, 100))
        assertFalse(canCopyCoverUnchanged(100, 0, 100))
    }
    @Test fun `staging preserves bytes and uses caller stream ownership`() {
        var inputClosed = false; var outputClosed = false
        val input = object : ByteArrayInputStream(byteArrayOf(1,2,3)) { override fun close() { inputClosed = true } }
        val output = object : ByteArrayOutputStream() { override fun close() { outputClosed = true } }
        assertEquals(3L, copyCoverSource(input, output, limit = 3))
        assertArrayEquals(byteArrayOf(1,2,3), output.toByteArray())
        assertFalse(inputClosed); assertFalse(outputClosed)
    }
    @Test fun `staging actual byte cap also applies when source length is unknown`() {
        val output = ByteArrayOutputStream()
        try { copyCoverSource(ByteArrayInputStream(ByteArray(100)), output, limit = 3); fail() }
        catch (_: IllegalStateException) { }
        assertTrue(output.size() <= 3)
        assertEquals(0L, copyCoverSource(ByteArrayInputStream(byteArrayOf()), output, limit = 0))
    }
    @Test fun `staging cancellation propagates before any output`() {
        val output = ByteArrayOutputStream()
        try { copyCoverSource(ByteArrayInputStream(ByteArray(10)), output, { throw CancellationException() }); fail() }
        catch (_: CancellationException) { }
        assertEquals(0, output.size())
    }
    @Test fun `encoded single and bulk writes share hard limit and flag overflow`() {
        val bytes = ByteArrayOutputStream()
        CoverEncodedOutput(bytes, 3).use { output ->
            output.write(1); output.write(byteArrayOf(2,3), 0, 2)
            output.write(byteArrayOf(), 0, 0)
            assertFalse(output.exceeded)
            try { output.write(4); fail() } catch (_: IOException) { }
            assertTrue(output.exceeded)
        }
        assertArrayEquals(byteArrayOf(1,2,3), bytes.toByteArray())
    }
    @Test fun `disk errors propagate without being mistaken for quality overflow`() {
        val target = object : java.io.OutputStream() { override fun write(value: Int) { throw IOException("fixture") } }
        val output = CoverEncodedOutput(target, 10)
        try { output.write(1); fail() } catch (_: IOException) { }
        assertFalse(output.exceeded)
    }
}
