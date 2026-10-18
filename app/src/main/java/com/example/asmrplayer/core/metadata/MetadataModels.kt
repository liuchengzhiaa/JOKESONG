package com.example.asmrplayer.core.metadata

import java.util.UUID

val EditableTagKeys = listOf("TITLE", "ARTIST", "ALBUM", "ALBUMARTIST", "TRACKNUMBER", "DISCNUMBER", "DATE", "GENRE", "COMMENT")
internal val WritableAudioExtensions = setOf("mp3", "flac", "m4a", "wav")
private val artifactPattern = Regex("^\\.__asmr-[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}-(?:stage|backup)\\.(?:mp3|flac|m4a|wav)$")
fun isMetadataArtifactName(name: String): Boolean = artifactPattern.matches(name)
internal fun newEditJournal(name: String): EditJournal {
    val token = UUID.randomUUID().toString(); val extension = name.substringAfterLast('.').lowercase()
    require(extension in WritableAudioExtensions && !isMetadataArtifactName(name))
    return EditJournal(name, ".__asmr-$token-stage.$extension", ".__asmr-$token-backup.$extension")
}

data class TagEditSnapshot(val fileName: String, val values: Map<String, List<String>>, val sourceVersion: String) {
    fun text(key: String) = values[key].orEmpty().joinToString("; ")
}

internal fun editedProperties(before: Map<String, List<String>>, fields: Map<String, String>): Map<String, List<String>> {
    val result = before.toMutableMap()
    fields.forEach { (key, value) ->
        require(key in EditableTagKeys)
        require(value.length <= if (key == "COMMENT") 8192 else 2048)
        require('\u0000' !in value)
        if (value != before[key].orEmpty().joinToString("; ")) {
            if (value.isBlank()) result.remove(key) else result[key] = listOf(value.trim())
        }
    }
    return result
}

sealed interface CoverEdit {
    data object Keep : CoverEdit
    data object RemoveFront : CoverEdit
    data class Replace(val jpeg: ByteArray) : CoverEdit
}

data class TagSaveResult(val documentUri: String, val title: String, val artist: String?,
    val artworkUri: String, val cleanupPending: Boolean)
