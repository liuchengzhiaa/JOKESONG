package com.example.asmrplayer.core.storage

import java.util.Locale

object SupportedAudio {
    private val extensions = setOf("mp3", "flac", "wav", "m4a", "aac", "ogg", "opus")
    private val extraMimeTypes = setOf(
        "application/ogg",
        "application/opus",
    )

    fun isSupported(displayName: String, mimeType: String?): Boolean {
        val extension = displayName.substringAfterLast('.', missingDelimiterValue = "").lowercase(Locale.ROOT)
        return extension in extensions || mimeType?.lowercase(Locale.ROOT)?.let {
            it.startsWith("audio/") || it in extraMimeTypes
        } == true
    }

    fun extensionOf(displayName: String): String =
        displayName.substringAfterLast('.', missingDelimiterValue = "").uppercase(Locale.ROOT)
}
