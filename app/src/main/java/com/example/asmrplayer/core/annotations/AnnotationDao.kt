package com.example.asmrplayer.core.annotations

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
abstract class AnnotationDao {
    @Query("SELECT t.id, t.name, COUNT(it.documentUri) AS itemCount FROM user_tags t LEFT JOIN user_item_tags it ON it.tagId=t.id AND it.rootUri=t.rootUri WHERE t.rootUri=:root AND t.name LIKE :pattern ESCAPE '\\' GROUP BY t.id ORDER BY t.nameKey, t.id LIMIT :limit OFFSET :offset")
    abstract fun tags(root: String, pattern: String, limit: Int, offset: Int): Flow<List<TagSummary>>
    @Query("SELECT * FROM user_items i WHERE i.rootUri=:root AND (:tagId IS NULL OR EXISTS(SELECT 1 FROM user_item_tags it WHERE it.rootUri=i.rootUri AND it.documentUri=i.documentUri AND it.tagId=:tagId)) AND (:score IS NULL OR (i.kind='Audio' AND i.score=:score)) ORDER BY i.title COLLATE NOCASE, i.documentUri LIMIT :limit OFFSET :offset")
    abstract fun items(root: String, tagId: Long?, score: Int?, limit: Int, offset: Int): Flow<List<UserItemEntity>>
    @Query("SELECT * FROM user_items WHERE rootUri=:root AND documentUri=:uri")
    abstract suspend fun item(root: String, uri: String): UserItemEntity?
    @Query("SELECT score FROM user_items WHERE rootUri=:root AND documentUri=:uri")
    abstract fun score(root: String, uri: String): Flow<Int?>
    @Query("SELECT t.* FROM user_tags t INNER JOIN user_item_tags it ON it.tagId=t.id WHERE it.rootUri=:root AND it.documentUri=:uri AND t.rootUri=:root")
    abstract suspend fun assigned(root: String, uri: String): List<UserTagEntity>
    @Query("SELECT * FROM user_tags WHERE rootUri=:root AND nameKey=:key")
    abstract suspend fun tag(root: String, key: String): UserTagEntity?
    @Query("SELECT * FROM user_tags WHERE id=:id AND rootUri=:root")
    abstract suspend fun tagById(root: String, id: Long): UserTagEntity?
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertTag(tag: UserTagEntity): Long
    @Upsert protected abstract suspend fun upsertItem(item: UserItemEntity)
    @Query("DELETE FROM user_item_tags WHERE rootUri=:root AND documentUri=:uri")
    protected abstract suspend fun clearAssignments(root: String, uri: String)
    @Insert protected abstract suspend fun insertAssignments(rows: List<UserItemTagEntity>)

    @Query("SELECT * FROM user_items WHERE rootUri=:root AND documentUri>:after ORDER BY documentUri LIMIT 50")
    abstract suspend fun exportItems(root: String, after: String): List<UserItemEntity>
    @Query("SELECT * FROM user_tags WHERE rootUri=:root AND id>:after ORDER BY id LIMIT 50")
    abstract suspend fun exportTags(root: String, after: Long): List<UserTagEntity>

    @Transaction
    open suspend fun restoreTag(root: String, name: String) {
        val normalized = AnnotationPolicy.tagName(name)
        insertTag(UserTagEntity(rootUri = root, name = normalized, nameKey = AnnotationPolicy.tagKey(normalized)))
    }
    @Transaction
    open suspend fun mergeImport(snapshot: UserItemEntity, names: List<String>) {
        val existing = item(snapshot.rootUri, snapshot.documentUri)
        val merged = PortableAnnotations.mergeTags(assigned(snapshot.rootUri, snapshot.documentUri).map { it.name }, names)
        save(snapshot, emptySet(), merged, existing?.score ?: snapshot.score)
    }

    /** One transaction: a bad tag/root/score cannot leave a half-saved annotation. */
    @Transaction
    open suspend fun save(snapshot: UserItemEntity, selected: Set<Long>, newNames: List<String>, score: Int?) {
        require(AnnotationPolicy.sameTree(snapshot.rootUri, snapshot.documentUri))
        require(score == null || (snapshot.kind == "Audio" && score in 0..100))
        require(snapshot.kind == "Audio" || snapshot.kind == "Folder")
        val ids = selected.map { requireNotNull(tagById(snapshot.rootUri, it)).id }.toMutableSet()
        val names = newNames.map(AnnotationPolicy::tagName).distinctBy(AnnotationPolicy::tagKey)
        require(ids.size + names.size <= AnnotationPolicy.MaxTagsPerItem)
        for (name in names) {
            val key = AnnotationPolicy.tagKey(name)
            insertTag(UserTagEntity(rootUri = snapshot.rootUri, name = name, nameKey = key))
            ids.add(requireNotNull(tag(snapshot.rootUri, key)).id)
        }
        val old = item(snapshot.rootUri, snapshot.documentUri)
        upsertItem(snapshot.copy(score = score, parentUri = snapshot.parentUri ?: old?.parentUri,
            lyricUri = snapshot.lyricUri ?: old?.lyricUri, lyricName = snapshot.lyricName ?: old?.lyricName,
            lyricFormat = snapshot.lyricFormat ?: old?.lyricFormat,
            lyricSize = if (snapshot.lyricUri == null) old?.lyricSize ?: 0 else snapshot.lyricSize,
            lyricModified = if (snapshot.lyricUri == null) old?.lyricModified ?: 0 else snapshot.lyricModified,
            lyricLanguage = snapshot.lyricLanguage ?: old?.lyricLanguage))
        clearAssignments(snapshot.rootUri, snapshot.documentUri)
        insertAssignments(ids.map { UserItemTagEntity(snapshot.rootUri, snapshot.documentUri, it) })
    }
}
