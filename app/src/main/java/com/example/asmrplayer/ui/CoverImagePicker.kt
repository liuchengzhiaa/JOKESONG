package com.example.asmrplayer.ui

import android.content.Intent
import android.provider.DocumentsContract

/** Shared SAF start-location hint; providers may fall back to their last location. */
internal fun coverImagePickerIntent() = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
    addCategory(Intent.CATEGORY_OPENABLE)
    type = "image/*"
    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    putExtra(
        DocumentsContract.EXTRA_INITIAL_URI,
        DocumentsContract.buildDocumentUri(
            "com.android.externalstorage.documents", "primary:Download/mihon/local",
        ),
    )
}
