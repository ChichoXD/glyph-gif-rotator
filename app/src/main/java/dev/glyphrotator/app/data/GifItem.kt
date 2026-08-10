package dev.glyphrotator.app.data

import android.net.Uri

data class GifItem(
    val id: String,
    val uri: Uri,
    val displayName: String
)
