package com.example.asmrplayer.core.card

import android.content.Context
import android.net.Uri
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Turns a cover and some copy into a card on disk.
 *
 * The three layers and the config are written together or not at all: a directory with
 * half a card in it would show up in the warehouse as a card that cannot open, and the
 * owner would have no way to tell it apart from one that works. A failure anywhere
 * removes the directory it was building into.
 */
internal class CardBuilder(context: Context) {

    private val context = context.applicationContext
    private val repository = CardRepository(this.context)
    private val layers = CardLayerFactory(this.context)

    /**
     * Builds one card, and returns its id, or null if the cover could not be read.
     *
     * Null means the cover, and only the cover: a cover that cannot be decoded is the
     * one failure the owner can act on, by picking another one. Everything else that can
     * go wrong here is a bug rather than an input, and is allowed to be one.
     *
     * [mediaId] is the library item the cover came from, and may be null: a card built
     * from a picture the owner picked out of their gallery belongs to no song. It is
     * recorded rather than derived, because nothing about a card's files names the audio
     * it was struck from -- and without it, "this song already has a card" has no answer.
     */
    suspend fun create(
        artworkUri: Uri?,
        fields: CardTextFields,
        preset: CardStylePreset,
        mediaId: String? = null,
    ): String? = withContext(Dispatchers.IO) {
        val directory = repository.createCard()
        val built = layers.build(artworkUri, fields, directory)
        if (built == null) {
            directory.deleteRecursively()
            return@withContext null
        }
        File(directory, CardConfig.ConfigFileName)
            .writeText(CardConfig(fields, preset).toJson())
        repository.writeMeta(
            directory,
            CardMeta(
                mediaId = mediaId?.takeIf(String::isNotEmpty),
                source = CardSource.Local,
                createdAtMillis = System.currentTimeMillis(),
            ),
        )
        directory.name
    }
}
