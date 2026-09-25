package com.example.asmrplayer.core.storage

import com.example.asmrplayer.core.model.LibraryItem

/**
 * The last known listing of one visited folder, together with the focus-wall
 * state that belonged to it.
 *
 * The snapshot exists so that leaving and re-entering a folder restores it from
 * memory instead of querying the document provider again. Only an explicit
 * refresh invalidates an entry.
 */
data class FolderSnapshot(
    val folderKey: String,
    val folderTitle: String,
    val items: List<LibraryItem>,
    val supportedItemCount: Int,
    val isTruncated: Boolean,
    val focusedItemId: String? = null,
    val canvasZoom: Float = 1f,
    val panX: Float = 0f,
    val panY: Float = 0f,
    /** Mirrors `FocusWallDensity` ordinal; the cache stays free of UI types. */
    val wallDensityOrdinal: Int = DefaultWallDensityOrdinal,
) {
    companion object {
        /** `FocusWallDensity.Medium.ordinal`. */
        const val DefaultWallDensityOrdinal: Int = 1
    }
}

/**
 * Bounded least-recently-used store of [FolderSnapshot]s.
 *
 * The library is expected to reach 10,000 folders, so only the most recently
 * visited ones are worth keeping. Reads count as use, which keeps a folder the
 * user keeps returning to resident while the oldest visit is evicted first.
 */
class FolderSnapshotCache(private val capacity: Int = DefaultCapacity) {
    init {
        require(capacity > 0) { "FolderSnapshotCache capacity must be positive" }
    }

    private val entries = object : LinkedHashMap<String, FolderSnapshot>(
        /* initialCapacity = */ 16,
        /* loadFactor = */ 0.75f,
        /* accessOrder = */ true,
    ) {
        override fun removeEldestEntry(
            eldest: MutableMap.MutableEntry<String, FolderSnapshot>,
        ): Boolean = size > capacity
    }

    val size: Int get() = entries.size

    fun contains(folderKey: String): Boolean = entries.containsKey(folderKey)

    fun get(folderKey: String): FolderSnapshot? = entries[folderKey]

    fun put(snapshot: FolderSnapshot) {
        entries[snapshot.folderKey] = snapshot
    }

    /**
     * Replaces a resident snapshot. Returns false when the folder has no
     * snapshot yet, so a caller can never accidentally cache a partial listing.
     */
    fun update(folderKey: String, transform: (FolderSnapshot) -> FolderSnapshot): Boolean {
        val current = entries[folderKey] ?: return false
        entries[folderKey] = transform(current)
        return true
    }

    fun invalidate(folderKey: String) {
        entries.remove(folderKey)
    }

    fun clear() {
        entries.clear()
    }

    companion object {
        const val DefaultCapacity: Int = 12
    }
}
