package com.fjr.docscanner.data

import android.content.Context
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import org.koin.core.annotation.Single

class DocumentContentObserver(
    private val scope: CoroutineScope,
    private val onUpdateImg: () -> Unit,
    private val onUpdatePdf: () -> Unit
) : ContentObserver(Handler(Looper.getMainLooper())) {
    override fun onChange(self: Boolean, uri: Uri?) {
        super.onChange(self, uri)
        // Call the update function in the coroutine scope
        println("uri $uri ${uri?.path}" )
        scope.launch {
            when {
                uri != null && uri.toString().contains("media/external/images/media") -> {
                    scope.launch {
                        onUpdateImg() // Call update for images
                    }
                }

                uri != null && (uri.toString()
                    .contains("media/external_primary/file") || uri.toString()
                    .contains("media/external/file")) -> {
                    scope.launch {
                        onUpdatePdf() // Call update for PDFs
                    }
                }
            }
        }
    }
}

@Single
class ScannerContentObserver(
    val context: Context,
    val coroutineScope: CoroutineScope
) {

    private lateinit var pdfContentObserver: DocumentContentObserver
    private lateinit var imgContentObserver: DocumentContentObserver

    fun startObservingDocuments(onUpdateImg: () -> Unit, onUpdatePdf: () -> Unit) {
        val contentResolver = context.contentResolver ?: return

        imgContentObserver = DocumentContentObserver(coroutineScope, onUpdateImg = {
            println("== change in image")
            onUpdateImg()
        }, onUpdatePdf = {})

        pdfContentObserver =
            DocumentContentObserver(coroutineScope, onUpdateImg = {}, onUpdatePdf = {
                println("== change in pdf")
                onUpdatePdf()
            })

        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            imgContentObserver
        )

        contentResolver.registerContentObserver(
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            } else {
                MediaStore.Files.getContentUri("external")
            },
            true,
            pdfContentObserver
        )
    }

    fun stopObservingDocuments() {
        val contentResolver = context.contentResolver ?: return
        contentResolver.unregisterContentObserver(imgContentObserver)
        contentResolver.unregisterContentObserver(pdfContentObserver)
    }

}