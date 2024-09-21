package com.fjr.docscanner.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.database.ContentObserver
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.fjr.docscanner.R
import com.fjr.docscanner.data.util.toBitmap
import com.fjr.docscanner.data.util.toImageBitmap
import com.fjr.docscanner.domain.model.DocImg
import com.fjr.docscanner.domain.model.DocPdf
import com.fjr.docscanner.domain.util.DataError
import com.fjr.docscanner.domain.util.EmptyResult
import com.fjr.docscanner.domain.util.Result
import com.fjr.docscanner.domain.util.asEmptyDataResult
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import java.io.InputStream
import java.io.OutputStream

class DocumentContentObserver(
    private val scope: CoroutineScope,
    private val onUpdateImg: () -> Unit,
    private val onUpdatePdf: () -> Unit
) : ContentObserver(Handler(Looper.getMainLooper())) {
    override fun onChange(self: Boolean, uri: Uri?) {
        super.onChange(self, uri)
        // Call the update function in the coroutine scope
        println("uri $uri")
        scope.launch {
            when {
                uri != null && uri.toString().contains("media/external/images/media") -> {
                    scope.launch {
                        onUpdateImg() // Call update for images
                    }
                }
                uri != null && uri.toString().contains("media/external_primary/file") -> {
                    scope.launch {
                        onUpdatePdf() // Call update for PDFs
                    }
                }
            }
        }
    }
}


@Single
class DataSource(
    val context: Context,
    val coroutineScope: CoroutineScope
) {
    companion object {
        const val DIRECTORY = "DocScanner"
    }

    private lateinit var imgContentObserver: DocumentContentObserver
    private lateinit var pdfContentObserver: DocumentContentObserver

    suspend fun saveDocPdf(pdfUri: Uri): EmptyResult<DataError.Storage> {
        val pdfName = "DocScan_${System.currentTimeMillis()}.pdf"

        // Define the content values for the new file in the public Documents/DocScan directory
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, pdfName) // File name
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf") // MIME type
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOCUMENTS + "/"+DIRECTORY // Save to Documents/DocScan directory

            )
        }

        // Get the appropriate URI for saving the file in the Documents directory based on Android version
        val pdfCollectionUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Files.getContentUri("external")
        }

        return withContext(Dispatchers.IO) {
            try {
                // Insert the new file into the MediaStore with the specified path
                val destinationUri: Uri =
                    context.contentResolver.insert(pdfCollectionUri, contentValues)
                        ?: return@withContext Result.Failed(DataError.Storage.ERROR_SAVING)

                // Open the input stream to read from the existing PDF file
                val inputStream: InputStream = context.contentResolver.openInputStream(pdfUri)
                    ?: return@withContext Result.Failed(DataError.Storage.ERROR_SAVING)

                // Open the output stream to write to the new file location
                val outputStream: OutputStream =
                    context.contentResolver.openOutputStream(destinationUri)
                        ?: return@withContext Result.Failed(DataError.Storage.ERROR_SAVING)

                try {
                    // Use a 4 KB buffer for better performance
                    val buffer = ByteArray(4096) // 4 KB buffer size
                    var bytesRead: Int

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                    outputStream.flush()

                    Result.Success(Unit).asEmptyDataResult()
                } catch (e: Exception) {
                    Result.Failed(DataError.Storage.ERROR_SAVING)
                } finally {
                    // Close both streams safely
                    inputStream.close()
                    outputStream.close()
                }
            } catch (e: Exception) {
                Result.Failed(DataError.Storage.ERROR_SAVING)
            }
        }
    }

    suspend fun readDocPdf(): Result<List<DocPdf>, DataError.Storage> {
        return withContext(Dispatchers.IO) {
            val contentResolver = context.contentResolver ?: return@withContext Result.Failed(DataError.Storage.ERROR_READING)
            val result: ArrayList<DocPdf> = arrayListOf()

            try {
                // Use MediaStore.Files to query for PDF files
                val uriExternal: Uri = MediaStore.Files.getContentUri("external")
                val projection = arrayOf(
                    MediaStore.Files.FileColumns._ID,
                    MediaStore.Files.FileColumns.DISPLAY_NAME,
                    MediaStore.Files.FileColumns.DATE_MODIFIED,
                    MediaStore.Files.FileColumns.MIME_TYPE
                )
                val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} = ? AND ${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE ?"
                val selectionArgs = arrayOf(
                    "application/pdf",  // Filter only PDF files
                    "%/$DIRECTORY/%"    // Filter by directory
                )
                val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"

                contentResolver.query(
                    uriExternal,
                    projection,
                    selection,
                    selectionArgs,
                    sortOrder
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                    val filenameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                    val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn)
                        val filename = cursor.getString(filenameColumn)
                        var dateModified = cursor.getLong(dateModifiedColumn)
                        val uri = ContentUris.withAppendedId(uriExternal, id)

                        if (dateModified == 0L) {
                            dateModified = System.currentTimeMillis() / 1000 // Convert milliseconds to seconds
                        }

                        result.add(DocPdf(id, filename, uri, dateModified))
                    }
                }

                // Return success with the result list
                Result.Success(result)
            } catch (e: Exception) {
                // Handle any other exceptions that may occur
                e.printStackTrace()
                Result.Failed(DataError.Storage.ERROR_READING)
            }
        }
    }

    suspend fun saveDocImg(imageUri: Uri): EmptyResult<DataError.Storage> {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "DocScan_${System.currentTimeMillis()}")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/" + DIRECTORY
            )
        }

        return withContext(Dispatchers.IO) {
            try {

                val uri = context.contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                ) ?: return@withContext Result.Failed(DataError.Storage.ERROR_SAVING)

                val outputStream: OutputStream = context.contentResolver.openOutputStream(uri)
                    ?: return@withContext Result.Failed(DataError.Storage.ERROR_SAVING)

                try {
                    val bitmap = imageUri.toBitmap(context)
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    outputStream.flush()
                    Result.Success(Unit).asEmptyDataResult()
                } catch (e: Exception) {
                    Result.Failed(DataError.Storage.ERROR_SAVING)
                } finally {
                    outputStream.close()
                }

            } catch (e: Exception) {
                Result.Failed(DataError.Storage.ERROR_SAVING)
            }
        }
    }

    suspend fun readDocsImg(): Result<List<DocImg>, DataError.Storage> {
        return withContext(Dispatchers.IO) {
            val result: ArrayList<DocImg> = arrayListOf()

            val contentResolver = context.contentResolver ?: return@withContext Result.Failed(DataError.Storage.ERROR_READING)

            val uriExternal: Uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_MODIFIED
            )
            val selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
            val selectionArgs = arrayOf("%/$DIRECTORY/%")
            val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

            try {
                contentResolver.query(
                    uriExternal,
                    projection,
                    selection,
                    selectionArgs,
                    sortOrder
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    val filenameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                    val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn)
                        val filename = cursor.getString(filenameColumn)
                        val dateModified = cursor.getLong(dateModifiedColumn)
                        val uri = ContentUris.withAppendedId(uriExternal, id)

                        // Attempt to load the image bitmap, might be null if loading fails
                        val imageBitmap = uri.toImageBitmap(context)

                        // Add the document to the list
                        result.add(DocImg(id, filename, imageBitmap, uri, dateModified))
                    }
                }

                // Return success with the result list
                Result.Success(result)

            } catch (e: Exception) {
                // Handle any other exceptions that may occur
                e.printStackTrace()
                Result.Failed(DataError.Storage.ERROR_READING)
            }
        }
    }

    suspend fun deleteDocImg(uri: Uri): EmptyResult<DataError.Storage> {
        return withContext(Dispatchers.IO) {
            try {
                val rowsDeleted = context.contentResolver.delete(uri, null, null)
                if (rowsDeleted > 0) {
                    Result.Success(Unit).asEmptyDataResult() // Successfully deleted
                } else {
                    Result.Failed(DataError.Storage.ERROR_DELETING) // No rows were deleted
                }
            } catch (e: Exception) {
                e.printStackTrace() // Log the exception for debugging
                Result.Failed(DataError.Storage.ERROR_DELETING)
            }
        }
    }

    fun startObservingDocuments(onUpdateImg: () -> Unit, onUpdatePdf: () -> Unit) {
        val contentResolver = context.contentResolver ?: return

        imgContentObserver = DocumentContentObserver(coroutineScope, onUpdateImg = {
            println("== change in image")
            onUpdateImg()
        }, onUpdatePdf = {})

        pdfContentObserver = DocumentContentObserver(coroutineScope, onUpdateImg = {}, onUpdatePdf = {
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
