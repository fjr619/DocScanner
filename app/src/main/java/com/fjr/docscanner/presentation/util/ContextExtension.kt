package com.fjr.docscanner.presentation.util

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast

fun Context.showToast(textId: Int) {
    Toast.makeText(
        this,
        getString(textId),
        Toast.LENGTH_SHORT
    ).show()
}

fun Context.openUri(uri: Uri, mimeType: String) {
    val intent = Intent(Intent.ACTION_VIEW).apply {
        setDataAndType(uri, mimeType) // Set the MIME type
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) // Grant permission to read the URI
    }

    startActivity(intent)

}