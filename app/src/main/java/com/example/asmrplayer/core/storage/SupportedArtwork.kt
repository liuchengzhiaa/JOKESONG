package com.example.asmrplayer.core.storage

import java.util.Locale

object SupportedArtwork {
    private val extensions = setOf("jpg", "jpeg", "png", "webp", "bmp", "heic", "heif", "avif")
    private val mimeTypes = setOf(
        "image/jpeg",
        "image/png",
        "image/webp",
        "image/bmp",
        "image/heic",
        "image/heif",
        "image/avif",
    )

    fun isSupported(displayName: String, mimeType: String?): Boolean {
        val extension = displayName.substringAfterLast('.', missingDelimiterValue = "").lowercase(Locale.ROOT)
        return extension in extensions || mimeType?.lowercase(Locale.ROOT)?.let { it in mimeTypes } == true
    }
}
