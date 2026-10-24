package com.example.asmrplayer.core.artwork

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Generated metadata/private scratch DB only. Never opens or writes a user media tree. */
@RunWith(AndroidJUnit4::class)
class CoverDirectoryCatalogTest {
    @Test fun manyPagesEnumerateSourceOnlyOnceAndPreserveNaturalOrder() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        var scans = 0
        CoverDirectoryCatalog(ctx.cacheDir, {}).use { index ->
            val load = {
                scans++
                val rows = CoverDirectoryRows()
                (1000 downTo 1).forEach { rows.add(CoverDocument("u$it", "$it"), CoverDirectoryRow.Folder) }
                rows.add(CoverDocument("cover", "cover.png"), CoverDirectoryRow.Cover)
                rows.sorted()
            }
            var after: CoverDocument? = null
            val seen = mutableListOf<String>()
            do {
                index.prepare("root", load)
                val page = index.page("root", CoverChildKind.Folder, after, 32)
                seen += page.map { it.name }
                after = page.lastOrNull()
            } while (after != null)
            assertEquals(1, scans); assertEquals((1..1000).map(Int::toString), seen)
            assertEquals("cover", index.cover("root"))
        }
    }
    @Test fun imagePagesShareSingleCatalogScanAndExcludeOtherKinds() {
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        var scans = 0
        CoverDirectoryCatalog(ctx.cacheDir, {}).use { index ->
            val load = {
                scans++
                val rows = CoverDirectoryRows()
                (70 downTo 1).forEach { rows.add(CoverDocument("img$it", "$it.jpg"), CoverDirectoryRow.Image) }
                rows.add(CoverDocument("cover", "cover.png"), CoverDirectoryRow.Cover)
                rows.add(CoverDocument("song", "song.mp3"), CoverDirectoryRow.Audio)
                rows.add(CoverDocument("child", "child"), CoverDirectoryRow.Folder)
                rows.sorted()
            }
            val seen = mutableListOf<String>()
            var after: CoverDocument? = null
            do {
                index.prepare("root", load)
                val page = index.page("root", CoverChildKind.Image, after, 32)
                seen += page.map { it.uri }
                after = page.lastOrNull()
            } while (after != null)
            assertEquals(1, scans); assertEquals((1..70).map { "img$it" }, seen)
            assertEquals("cover", index.cover("root"))
            assertEquals(listOf("song"), index.page("root", CoverChildKind.Audio, null, 32).map { it.uri })
            assertEquals(listOf("child"), index.page("root", CoverChildKind.Folder, null, 32).map { it.uri })
        }
    }

}
