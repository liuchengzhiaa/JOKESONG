package com.example.asmrplayer.core.artwork

import androidx.core.net.toUri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.asmrplayer.core.database.AsmrDatabase
import com.example.asmrplayer.core.database.ArtworkEntity
import com.example.asmrplayer.core.model.LibraryItem
import com.example.asmrplayer.core.model.LibraryItemType
import java.io.File
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Disposable app-cache fixtures only; no SAF media tree or real user image is opened. */
@RunWith(AndroidJUnit4::class)
class FolderCoverRepositoryTest {
    @Test fun savedOverrideSurvivesRepositoryRecreationAndResetRestoresAutomaticArt() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AsmrDatabase::class.java).build()
        val dir = File(context.cacheDir, "folder-cover-test-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        val image = File(dir, "test.png").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val uri = "content://generated/folder"
        val folder = LibraryItem(uri, "work", "", LibraryItemType.Folder, paletteSeed = 1,
            documentUri = uri, artworkUri = "automatic")
        try {
            db.libraryDao().upsertArtwork(listOf(ArtworkEntity("manual-folder:$uri", uri,
                "manual_folder", image.toUri().toString(), "image/png", null, null, "test", 1)))
            val repository = FolderCoverRepository(context, db.libraryDao(), dir)
            assertEquals(image.toUri().toString(), repository.project(listOf(folder)).single().artworkUri)
            val recreated = FolderCoverRepository(context, db.libraryDao(), dir)
            assertEquals(image.toUri().toString(), recreated.project(listOf(folder)).single().artworkUri)
            // Refuse file:// input; a failed selection preserves the old row and file.
            try { recreated.set(uri, image.toUri()); fail("Must require a SAF content URI") }
            catch (_: IllegalArgumentException) { }
            assertTrue(image.exists())
            recreated.set(uri, null)
            assertFalse(image.exists())
            assertEquals("automatic", recreated.project(listOf(folder)).single().artworkUri)
        } finally {
            db.close()
            image.delete()
            dir.delete()
        }
    }

    @Test fun foreignFileReferenceIsNeverOpenedOrDeleted() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.inMemoryDatabaseBuilder(context, AsmrDatabase::class.java).build()
        val dir = File(context.cacheDir, "folder-cover-test-${java.util.UUID.randomUUID()}").apply { mkdirs() }
        val outside = File.createTempFile("generated-unowned-", ".png", context.cacheDir)
        val uri = "content://generated/folder"
        val folder = LibraryItem(uri, "work", "", LibraryItemType.Folder, paletteSeed = 1,
            documentUri = uri, artworkUri = "automatic")
        try {
            db.libraryDao().upsertArtwork(listOf(ArtworkEntity("manual-folder:$uri", uri,
                "manual_folder", outside.toUri().toString(), "image/png", null, null, "test", 1)))
            val repository = FolderCoverRepository(context, db.libraryDao(), dir)
            assertEquals("automatic", repository.project(listOf(folder)).single().artworkUri)
            repository.set(uri, null)
            assertTrue(outside.exists())
        } finally {
            db.close()
            outside.delete()
            dir.delete()
        }
    }
}
