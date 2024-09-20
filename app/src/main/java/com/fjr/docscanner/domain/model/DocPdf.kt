package com.fjr.docscanner.domain.model

import android.net.Uri

data class DocPdf(
    val id: Long,
    val filename: String,
    val uri: Uri,
    val dateModified: Long
)