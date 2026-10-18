package com.example.asmrplayer.core.storage

import com.example.asmrplayer.core.model.NaturalNameComparator

internal data class FolderArtworkEntry(val name: String, val uri: String, val version: String = "")

/** Constant-space selection independent of provider cursor ordering. Images take priority. */
internal class FolderArtworkSelection {
    var image: FolderArtworkEntry? = null
        private set
    var firstAudio: FolderArtworkEntry? = null
        private set

    fun image(candidate: FolderArtworkEntry) { image = earlier(image, candidate) }
    fun audio(candidate: FolderArtworkEntry) { firstAudio = earlier(firstAudio, candidate) }

    private fun earlier(current: FolderArtworkEntry?, next: FolderArtworkEntry): FolderArtworkEntry {
        if (current == null) return next
        val comparison = NaturalNameComparator.compare(next.name, current.name)
        return if (comparison < 0 || comparison == 0 && next.uri < current.uri) next else current
    }
}
