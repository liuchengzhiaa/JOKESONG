package com.example.asmrplayer.core.storage

import android.content.ContentProvider
import android.content.ContentResolver
import android.content.ContentValues
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract as Docs
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.io.FileNotFoundException
import java.util.concurrent.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Generated metadata provider only: proves browsing I/O, never uses real media or storage grants. */
@RunWith(AndroidJUnit4::class)
class FolderBrowsingIoTest {
    private val tree = Uri.parse("content://com.example.jokesong.fixture.documents/tree/root")
    private val folder get() = Docs.buildDocumentUriUsingTree(tree, "root")
    private data class Row(val id: String, val name: String, val mime: String)
    private class Provider(val rows: List<Row>, var nullQuery: Boolean = false) : ContentProvider() {
        val queries = mutableListOf<Uri>()
        val projections = mutableListOf<List<String>>()
        var opens = 0
        var cursor: MatrixCursor? = null
        override fun onCreate() = true
        override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor? {
            queries += uri; val columns = projection!!.toList(); projections += columns
            if (nullQuery) return null
            val result = MatrixCursor(projection)
            if (uri.lastPathSegment == "children") {
                check(Docs.getDocumentId(uri) == "root") { "Unexpected eager child enumeration" }
                for (row in rows) {
                    val values: List<Any> = columns.map { column -> when(column) {
                    Docs.Document.COLUMN_DOCUMENT_ID -> row.id
                    Docs.Document.COLUMN_DISPLAY_NAME -> row.name
                    Docs.Document.COLUMN_MIME_TYPE -> row.mime
                    Docs.Document.COLUMN_SIZE -> 123L
                    Docs.Document.COLUMN_LAST_MODIFIED -> 456L
                    else -> error("Unexpected projection")
                    } }
                    result.addRow(values.toTypedArray())
                }
            } else result.addRow(columns.map { "Fixture root" }.toTypedArray())
            cursor = result
            return result
        }
        override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor { opens++; throw FileNotFoundException("Metadata only") }
        override fun getType(uri: Uri): String? = null
        override fun insert(uri: Uri, values: ContentValues?): Uri? = error("Read only")
        override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = error("Read only")
        override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = error("Read only")
    }
    @Test fun tenThousandFoldersProduceBoundedListingWithZeroChildQueriesOrMediaOpens() = runBlocking {
        val provider = Provider((10000 downTo 1).map { Row("root/f$it", "Folder$it", Docs.Document.MIME_TYPE_DIR) })
        val listing = SafDirectoryScanner(ContentResolver.wrap(provider)).scan(tree, folder)
        assertEquals(10000, listing.supportedItemCount); assertEquals(500, listing.items.size); assertTrue(listing.isTruncated)
        assertEquals("Folder1", listing.items.first().title); assertEquals("Folder500", listing.items.last().title)
        assertTrue(listing.items.all { it.artworkUri?.contains("/folder?") == true })
        assertEquals(2, provider.queries.size) // Current listing + its own title, NOT first48 child listings.
        assertEquals(1, provider.queries.count { it.lastPathSegment == "children" })
        assertEquals(0, provider.opens)
    }
    @Test fun directCoverLookupUsesOnlyThreeColumnsAndNeverInfersAudioOrDescendantArt() {
        val rows = (1..2000).map { Row("root/t$it", "Track$it.mp3", "audio/mpeg") } + listOf(
            Row("root/child", "child", Docs.Document.MIME_TYPE_DIR),
            Row("root/other", "picture.png", "image/png"),
            Row("root/backup", ".jokesong-cover-test-backup.jpg", "image/jpeg"),
            Row("root/fake", "cover.mp3", "audio/mpeg"),
            Row("root/directory", "cover.jpg", Docs.Document.MIME_TYPE_DIR),
            Row("root/webp", "cover.webp", "image/webp"),
            Row("root/jpeg", "cover.jpg", "image/jpeg"))
        val provider = Provider(rows)
        val result = readDirectFolderArtwork(ContentResolver.wrap(provider), tree, folder)
        assertEquals(Docs.buildDocumentUriUsingTree(tree, "root/jpeg").toString(), result.artworkUri)
        assertEquals(1, provider.queries.size); assertEquals(3, provider.projections.single().size)
        assertEquals(0, provider.opens); assertTrue(provider.cursor!!.isClosed)
    }
    @Test fun missingCoverDoesNotUseUnrelatedPicturesOrEmbeddedAudio() {
        val provider = Provider(listOf(Row("root/audio", "Track.mp3", "audio/mpeg"), Row("root/pic", "pic.png", "image/png")))
        assertNull(readDirectFolderArtwork(ContentResolver.wrap(provider), tree, folder).artworkUri)
        assertEquals(1, provider.queries.size); assertEquals(0, provider.opens)
    }
    @Test fun failedQueryIsNotSuccessfulAbsenceAndCancellationClosesCursor() {
        val provider = Provider(listOf(Row("root/cover", "cover.png", "image/png")), nullQuery = true)
        try { readDirectFolderArtwork(ContentResolver.wrap(provider), tree, folder); fail() }
        catch (_: java.io.IOException) { }
        provider.nullQuery = false; var checkpoints = 0
        try { readDirectFolderArtwork(ContentResolver.wrap(provider), tree, folder) { if (++checkpoints == 2) throw CancellationException() }; fail() }
        catch (_: CancellationException) { }
        assertTrue(provider.cursor!!.isClosed); assertEquals(0, provider.opens)
        assertNotNull(readDirectFolderArtwork(ContentResolver.wrap(provider), tree, folder).artworkUri)
    }
    @Test fun audioAndTimedTextMatchingArePreservedWithoutFolderStatistics() = runBlocking {
        val provider = Provider(listOf(Row("root/a", "Track.mp3", "audio/mpeg"),
            Row("root/l", "Track.lrc", "text/plain"), Row("root/f", "Folder", Docs.Document.MIME_TYPE_DIR)))
        val listing = SafDirectoryScanner(ContentResolver.wrap(provider)).scan(tree, folder)
        assertEquals(2, listing.supportedItemCount); assertEquals("Folder", listing.items.first().title)
        val audio = listing.items.last()
        assertEquals("Track", audio.title); assertTrue(audio.artworkUri!!.contains("/track?"))
        assertNotNull(listing.timedText[audio.id]); assertEquals(2, provider.queries.size); assertEquals(0, provider.opens)
    }
}
