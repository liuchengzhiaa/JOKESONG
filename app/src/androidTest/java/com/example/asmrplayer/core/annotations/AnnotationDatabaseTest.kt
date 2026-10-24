package com.example.asmrplayer.core.annotations

import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.asmrplayer.core.database.AsmrDatabase
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Generated private DB only: no library/provider/user media is opened. */
@RunWith(AndroidJUnit4::class)
class AnnotationDatabaseTest {
    private val root = "content://fixtures/tree/music"
    private fun audio(n: Int, kind: String = "Audio") = UserItemEntity(root,
        "$root/document/song$n", kind, "Song$n", "MP3", null, n, "$root/document/album")

    @Test fun upgradeKeepsV1RowsAndSupportsDurableRootScopedExactScores() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val name = "generated-annotation-migration-${System.nanoTime()}.db"
        val file = context.getDatabasePath(name)
        file.parentFile!!.mkdirs()
        try {
            val schema = JSONObject(instrumentation.context.assets.open(
                "com.example.asmrplayer.core.database.AsmrDatabase/1.json").bufferedReader().use { it.readText() })
                .getJSONObject("database")
            SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
                val entities = schema.getJSONArray("entities")
                for (i in 0 until entities.length()) {
                    val table = entities.getJSONObject(i)
                    fun sql(raw: String) = raw.replace("\${TABLE_NAME}", table.getString("tableName"))
                    db.execSQL(sql(table.getString("createSql")))
                    val indices = table.optJSONArray("indices") ?: org.json.JSONArray()
                    for (j in 0 until indices.length()) db.execSQL(sql(indices.getJSONObject(j).getString("createSql")))
                }
                val setup = schema.getJSONArray("setupQueries")
                for (i in 0 until setup.length()) db.execSQL(setup.getString(i))
                db.execSQL("INSERT INTO playback_bookmarks(audioDocumentUri,positionMs,label,createdAtMs) VALUES('generated',123,'keep',1)")
                db.version = 1
            }
            fun open() = Room.databaseBuilder(context, AsmrDatabase::class.java, name)
                .addMigrations(AnnotationMigration1To2).build()
            suspend fun useDb(block: suspend (AsmrDatabase) -> Unit) {
                val db = open()
                try { block(db) } finally { db.close() }
            }
            useDb { db ->
                db.openHelper.writableDatabase.query("SELECT label FROM playback_bookmarks").use { c ->
                    assertTrue(c.moveToFirst()); assertEquals("keep", c.getString(0))
                }
                val dao = db.annotationDao()
                dao.save(audio(1), emptySet(), listOf("Rain", "锛诧肌锛╋籍"), 90)
                dao.save(audio(2), emptySet(), listOf("rain"), 91)
                dao.save(audio(3, "Folder"), emptySet(), listOf("rain"), null)
                dao.save(audio(4), emptySet(), emptyList(), 0)
                val tags = dao.tags(root, "%", 51, 0).first()
                assertEquals(1, tags.size); assertEquals(3, tags.single().itemCount)
                assertEquals(listOf(audio(1).documentUri), dao.items(root, null, 90, 51, 0).first().map { it.documentUri })
                assertEquals(3, dao.items(root, tags.single().id, null, 51, 0).first().size)
                assertEquals(1, dao.items(root, null, 0, 51, 0).first().size)
                assertTrue(dao.items("other", null, 90, 51, 0).first().isEmpty())
                val assigned = dao.assigned(root, audio(1).documentUri).map { it.id }.toSet()
                assertTrue(runCatching { dao.save(audio(1), assigned + Long.MAX_VALUE, listOf("invalid"), 10) }.isFailure)
                assertEquals(90, dao.item(root, audio(1).documentUri)?.score)
                assertEquals(1, dao.tags(root, "%", 51, 0).first().size)
            }
            useDb { db ->
                assertEquals(90, db.annotationDao().item(root, audio(1).documentUri)?.score)
                assertEquals(1, db.annotationDao().assigned(root, audio(1).documentUri).size)
                db.annotationDao().save(audio(1), emptySet(), emptyList(), null)
                assertNull(db.annotationDao().item(root, audio(1).documentUri)?.score)
                assertTrue(db.annotationDao().assigned(root, audio(1).documentUri).isEmpty())
            }
        } finally { context.deleteDatabase(name) }
    }
}
