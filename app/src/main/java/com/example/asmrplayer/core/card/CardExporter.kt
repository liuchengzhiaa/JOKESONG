package com.example.asmrplayer.core.card

import android.content.ContentValues
import android.content.Context
import android.os.Environment
import android.provider.MediaStore

/**
 * Puts a finished card into the phone's picture library.
 *
 * No permission is involved and none may be added: every API here is the app inserting
 * and then owning its own entry, which is allowed from API 29 without any grant. The
 * app never reads the library, never enumerates it, and never deletes from it -- a card
 * the owner removes from the app stays in Pictures, because it is the owner's picture
 * now and this app is not its custodian.
 */
internal object CardExporter {

    const val AlbumName = "白相"

    private const val MimePng = "image/png"
    private const val FallbackName = "art-card.png"
    private const val MaxNameLength = 96

    /**
     * A display name the media store will accept.
     *
     * The name comes from the page, which derives it from the card's own title, and a
     * title is free text: it may hold a separator, a character the filesystem reserves,
     * or nothing at all. Trimming to one segment and stripping the reserved set is what
     * keeps a title from turning into a path or into a rejected insert.
     */
    fun displayName(raw: String): String {
        val lastSegment = raw.substringAfterLast('/').substringAfterLast('\\')
        val cleaned = lastSegment
            .filter { it.code >= 32 && it !in Reserved }
            .trim()
            .trimEnd('.')
        val stem = cleaned.removeSuffix(".png")
        if (stem.isEmpty()) return FallbackName
        return stem.take(MaxNameLength).ifEmpty { FallbackName }.plus(".png")
    }

    private val Reserved = charArrayOf(':', '*', '?', '"', '<', '>', '|')

    /**
     * Writes [bytes] into `Pictures/白相/`, and reports whether it landed.
     *
     * The entry is created pending and only published once the bytes are in it, so a
     * save that fails partway leaves nothing behind rather than a zero-byte image the
     * gallery will happily show. A failure deletes the pending row for the same reason.
     */
    fun toPictures(context: Context, displayName: String, bytes: ByteArray): Boolean {
        val resolver = context.contentResolver
        val pending = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, MimePng)
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                "${Environment.DIRECTORY_PICTURES}/$AlbumName",
            )
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val uri = runCatching {
            resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, pending)
        }.getOrNull() ?: return false

        val written = runCatching {
            resolver.openOutputStream(uri)?.use { it.write(bytes) } != null
        }.getOrDefault(false)
        if (!written) {
            runCatching { resolver.delete(uri, null, null) }
            return false
        }

        return runCatching {
            val published = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            resolver.update(uri, published, null, null)
            true
        }.getOrDefault(true)
    }
}
