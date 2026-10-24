package com.example.asmrplayer.core.artwork

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the production wrapper on ART, including its previously missing image entry point. */
@RunWith(AndroidJUnit4::class)
class LockedFolderCoverStoreTest {
    @Test fun imageFallbackThroughProductionWrapperCompletesAndPreservesCoverOnRerun() {
        val image = CoverDocument("generated", "art.png")
        var cover: String? = null
        var probes = 0
        val raw = object : FolderCoverStore {
            override fun existing(folder: String) = cover
            override fun legacy(folder: String): String? = null
            override fun page(folder: String, kind: CoverChildKind, after: CoverDocument?, limit: Int) =
                if (kind == CoverChildKind.Image && after == null) listOf(image) else emptyList()
            override fun image(candidate: CoverDocument): CoverImageCandidate {
                probes++; return CoverImageCandidate(candidate, 64, 32)
            }
            override fun embedded(audio: CoverDocument): String? = null
            override fun install(folder: String, source: String): String {
                assertEquals(image.uri, source)
                return cover ?: "$folder/cover.png".also { cover = it }
            }
        }
        val store = LockedFolderCoverStore(raw)
        assertEquals(1, FolderCoverBatch(store, {}).run("root").created)
        assertEquals(1, probes)
        assertEquals(1, FolderCoverBatch(store, {}).run("root").existing)
        assertEquals(1, probes)
        assertEquals("root/cover.png", cover)
    }
}
