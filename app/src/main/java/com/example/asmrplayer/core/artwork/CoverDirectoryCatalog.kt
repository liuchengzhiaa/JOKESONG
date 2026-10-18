package com.example.asmrplayer.core.artwork

import android.database.sqlite.SQLiteDatabase
import java.io.Closeable
import java.io.File

/** Disposable per-run metadata only. Never stores image bytes or survives as library truth. */
internal class CoverDirectoryCatalog(cacheDirectory: File, private val checkpoint: () -> Unit) : Closeable {
    private val file = File.createTempFile("cover-run-index-", ".db", cacheDirectory)
    private val db = SQLiteDatabase.openOrCreateDatabase(file, null)
    init {
        // Scratch data needs no durable journal/fsync: media-file writes retain their normal protection.
        db.disableWriteAheadLogging()
        db.rawQuery("PRAGMA journal_mode=MEMORY", null).use { check(it.moveToFirst()) }
        db.execSQL("PRAGMA synchronous=OFF")
        db.setMaximumSize(256L * 1024 * 1024)
        db.execSQL("CREATE TABLE folders (owner TEXT PRIMARY KEY)")
        db.execSQL("CREATE TABLE entries (owner TEXT, kind INTEGER, position INTEGER, name TEXT, uri TEXT, version TEXT, PRIMARY KEY(owner,kind,position))")
        db.execSQL("CREATE UNIQUE INDEX entry_uri ON entries(owner,kind,uri)")
    }
    fun prepare(folder: String, load: () -> List<CoverDirectoryRow>) {
        checkpoint()
        if (db.rawQuery("SELECT 1 FROM folders WHERE owner=?", arrayOf(folder)).use { it.moveToFirst() }) return
        val rows = load() // Exactly one SAF enumeration for this folder's traversal snapshot.
        db.beginTransaction()
        try {
            db.compileStatement("INSERT INTO entries VALUES(?,?,?,?,?,?)").use { statement ->
                rows.forEachIndexed { index, row ->
                    checkpoint()
                    statement.clearBindings()
                    statement.bindString(1, folder); statement.bindLong(2, row.kind.toLong())
                    statement.bindLong(3, index.toLong()); statement.bindString(4, row.document.name)
                    statement.bindString(5, row.document.uri); statement.bindString(6, row.document.version)
                    statement.executeInsert()
                }
            }
            db.execSQL("INSERT INTO folders VALUES(?)", arrayOf(folder))
            db.setTransactionSuccessful()
        } finally { db.endTransaction() }
    }
    fun cover(folder: String): String? = db.rawQuery(
        "SELECT uri FROM entries WHERE owner=? AND kind=2 ORDER BY position LIMIT 1", arrayOf(folder),
    ).use { if (it.moveToFirst()) it.getString(0) else null }
    fun page(folder: String, kind: CoverChildKind, after: CoverDocument?, limit: Int): List<CoverDocument> {
        checkpoint()
        val type = if (kind == CoverChildKind.Audio) CoverDirectoryRow.Audio else CoverDirectoryRow.Folder
        val position = if (after == null) -1L else db.rawQuery(
            "SELECT position FROM entries WHERE owner=? AND kind=? AND uri=?",
            arrayOf(folder, type.toString(), after.uri),
        ).use { check(it.moveToFirst()) { "COVER_INDEX_POSITION_MISSING" }; it.getLong(0) }
        return db.rawQuery("SELECT uri,name,version FROM entries WHERE owner=? AND kind=? AND position>? ORDER BY position LIMIT ?",
            arrayOf(folder, type.toString(), position.toString(), limit.toString())).use { c ->
            buildList { while (c.moveToNext()) { checkpoint(); add(CoverDocument(c.getString(0), c.getString(1), c.getString(2))) } }
        }
    }
    override fun close() {
        try { db.close() } finally { SQLiteDatabase.deleteDatabase(file) }
    }
}
