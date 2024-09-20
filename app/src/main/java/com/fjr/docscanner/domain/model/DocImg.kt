package com.fjr.docscanner.domain.model

import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap

data class DocImg(
    val id: Long,
    val filename: String,
    val imageBitmap: ImageBitmap,
    val uri: Uri,
    val dateModified: Long
)