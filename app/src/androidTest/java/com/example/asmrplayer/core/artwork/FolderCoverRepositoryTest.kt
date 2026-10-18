package com.example.asmrplayer.core.artwork

import android.provider.DocumentsContract
import androidx.core.net.toUri
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.asmrplayer.core.model.LibraryItem
import com.example.asmrplayer.core.model.LibraryItemType
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** URI/private-path checks only; never writes a user SAF tree. */
@RunWith(AndroidJUnit4::class)
class FolderCoverRepositoryTest {
    @Test fun folderProjectionUsesLazyDiskLookupAndLeavesAudioUnchanged() = runBlocking {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val uri = DocumentsContract.buildDocumentUriUsingTree(
            "content://com.android.externalstorage.documents/tree/primary%3AMusic".toUri(), "primary:Music/work").toString()
        val folder = LibraryItem(uri, "work", "", LibraryItemType.Folder, paletteSeed = 1, documentUri = uri, artworkUri = null)
        val audio = folder.copy(id = "audio", type = LibraryItemType.Audio, artworkUri = "audio-art")
        val repo = FolderCoverRepository(ctx)
        val projected = repo.project(listOf(folder, audio))
        assertEquals(audio, projected[1])
        assertEquals(uri, projected[0].artworkUri!!.toUri().getQueryParameter("folder"))
        assertEquals("/folder", projected[0].artworkUri!!.toUri().path)
        // No reset now: a real cover cannot be removed by deleting an app-private assignment.
        try { repo.set(uri, null); fail() } catch (_: IllegalArgumentException) { }
        try { repo.set(uri, "file:///test.png".toUri()); fail() } catch (_: IllegalArgumentException) { }
    }
    @Test fun onlyCanonicalOwnedLegacyPathsAreAccepted() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val owned = File(ctx.filesDir, "folder-covers/test.png")
        assertEquals(owned.canonicalFile, ownedLegacyCover(ctx, owned.toUri().toString()))
        assertNull(ownedLegacyCover(ctx, File(ctx.cacheDir, "outside.png").toUri().toString()))
        assertNull(ownedLegacyCover(ctx, File(ctx.filesDir, "folder-covers/../outside.png").toUri().toString()))
    }
    @Test fun generatedImagesValidateAtBoundedSizeAndInvalidImageIsRejected() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val fixture = File.createTempFile("generated-cover-", ".png", ctx.cacheDir)
        try {
            val bitmap = android.graphics.Bitmap.createBitmap(1800, 600, android.graphics.Bitmap.Config.ARGB_8888)
            try { fixture.outputStream().use { assertTrue(bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)) } }
            finally { bitmap.recycle() }
            val original = fixture.readBytes()
            assertEquals("image/png", validateFolderCoverImage(fixture))
            assertArrayEquals(original, fixture.readBytes())
            fixture.writeBytes(byteArrayOf(1, 2, 3))
            try { validateFolderCoverImage(fixture); fail() } catch (_: Exception) { }
        } finally { fixture.delete() }
    }
}
