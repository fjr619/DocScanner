package com.fjr.docscanner.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.ContentObserver
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import androidx.annotation.RequiresApi
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
import java.io.File
import java.io.FileOutputStream

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
        return withContext(Dispatchers.IO) {
            try {
                // Check Android version and call appropriate saving method
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // For Android 10 and above
                    savePdfUsingMediaStore(pdfUri)
                } else {
                    // For Android 9 and below
                    savePdfUsingFile(pdfUri)
                }
            } catch (e: Exception) {
                Result.Failed(DataError.Storage.ERROR_SAVING)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.Q)
    private fun savePdfUsingMediaStore(pdfUri: Uri): EmptyResult<DataError.Storage> {
        val pdfName = "DocScan_${System.currentTimeMillis()}.pdf"

        // Define the content values for the new file in the public Documents/DocScan directory
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, pdfName) // File name
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf") // MIME type
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_DOCUMENTS + "/$DIRECTORY" // Save to Documents/DocScan directory
            )
        }

        // Get the appropriate URI for saving the file in the Documents directory
        val pdfCollectionUri = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)

        return try {
            // Insert the new file into the MediaStore with the specified path
            val destinationUri: Uri =
                context.contentResolver.insert(pdfCollectionUri, contentValues)
                    ?: return Result.Failed(DataError.Storage.ERROR_SAVING)

            // Open the input stream to read from the existing PDF file
            context.contentResolver.openInputStream(pdfUri)?.use { inputStream ->
                // Open the output stream to write to the new file location
                context.contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
                    val buffer = ByteArray(4096) // 4 KB buffer size
                    var bytesRead: Int

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                    outputStream.flush()

                    Result.Success(Unit).asEmptyDataResult()
                } ?: Result.Failed(DataError.Storage.ERROR_SAVING) // Output stream is null
            } ?: Result.Failed(DataError.Storage.ERROR_SAVING) // Input stream is null

        } catch (e: Exception) {
            Result.Failed(DataError.Storage.ERROR_SAVING)
        }
    }

    private fun savePdfUsingFile(pdfUri: Uri): EmptyResult<DataError.Storage> {
        val pdfName = "DocScan_${System.currentTimeMillis()}.pdf"

        // Define the directory and file for the new PDF
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            DIRECTORY
        )
        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                return Result.Failed(DataError.Storage.ERROR_SAVING)
            }
        }

        val file = File(directory, pdfName)

        return try {
            // Open input stream to read from the existing PDF file
            context.contentResolver.openInputStream(pdfUri)?.use { inputStream ->
                // Open output stream to write to the new file location
                FileOutputStream(file).use { outputStream ->
                    val buffer = ByteArray(4096) // 4 KB buffer size
                    var bytesRead: Int

                    while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                        outputStream.write(buffer, 0, bytesRead)
                    }
                    outputStream.flush()

                    // Notify the MediaStore about the new file
                    context.sendBroadcast(
                        Intent(
                            Intent.ACTION_MEDIA_SCANNER_SCAN_FILE,
                            Uri.fromFile(file)
                        )
                    )

                    Result.Success(Unit).asEmptyDataResult()
                }
            } ?: Result.Failed(DataError.Storage.ERROR_SAVING) // Input stream is null

        } catch (e: Exception) {
            Result.Failed(DataError.Storage.ERROR_SAVING)
        }
    }

    suspend fun readDocPdf(): Result<List<DocPdf>, DataError.Storage> {
        return withContext(Dispatchers.IO) {
            try {
                val pdfList = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    println("readPdfUsingMediaStore")
                    readPdfUsingMediaStore()
                } else {
                    readPdfUsingFileSystem()
                }
                Result.Success(pdfList)
            } catch (e: Exception) {
                e.printStackTrace()
                Result.Failed(DataError.Storage.ERROR_READING)
            }
        }
    }

    private fun readPdfUsingMediaStore(): List<DocPdf> {
        val pdfList: ArrayList<DocPdf> = arrayListOf()

        val uriExternal: Uri = MediaStore.Files.getContentUri("external")
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.DATE_MODIFIED,
            MediaStore.Files.FileColumns.MIME_TYPE
        )
        val selection =
            "${MediaStore.Files.FileColumns.MIME_TYPE} = ? AND ${MediaStore.Files.FileColumns.RELATIVE_PATH} LIKE ?"
        val selectionArgs = arrayOf(
            "application/pdf",  // Filter only PDF files
            "%/$DIRECTORY/%"    // Filter by directory
        )
        val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"

        context.contentResolver.query(
            uriExternal,
            projection,
            selection,
            selectionArgs,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val filenameColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val dateModifiedColumn =
                cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val filename = cursor.getString(filenameColumn)
                var dateModified = cursor.getLong(dateModifiedColumn)
                val uri = ContentUris.withAppendedId(uriExternal, id)

                if (dateModified == 0L) {
                    dateModified =
                        System.currentTimeMillis() / 1000 // Convert milliseconds to seconds
                }

                pdfList.add(DocPdf(id, filename, uri, dateModified))
            }
        }

        return pdfList
    }

    private fun readPdfUsingFileSystem(): List<DocPdf> {
        val pdfList = mutableListOf<DocPdf>()

        // Define the directory to search for PDFs
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOCUMENTS),
            DIRECTORY
        )
        if (directory.exists() && directory.isDirectory) {
            // List all files with .pdf extension in the directory
            val files = directory.listFiles { _, name ->
                name.endsWith(".pdf", ignoreCase = true)
            }

            files?.forEach { file ->
                val id =
                    file.hashCode().toLong() // Generate a unique ID based on the file's hash code
                val filename = file.name
                val uri = Uri.fromFile(file)
                val dateModified = file.lastModified()

                pdfList.add(DocPdf(id, filename, uri, dateModified))
            }
        }

        return pdfList
    }

    suspend fun readDocsImg(): Result<List<DocImg>, DataError.Storage> {
        return withContext(Dispatchers.IO) {
            val result: ArrayList<DocImg> = arrayListOf()

            val contentResolver = context.contentResolver ?: return@withContext Result.Failed(
                DataError.Storage.ERROR_READING
            )

            val uriExternal: Uri = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
            val projection = arrayOf(
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_MODIFIED,
                MediaStore.Images.Media.DATA // This column is needed for Android 9 and below
            )

            // Check API level and adjust query accordingly
            val selection: String?
            val selectionArgs: Array<String>

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                // Android 10 and above: Use relative_path for filtering by directory
                selection = "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?"
                selectionArgs = arrayOf("%/$DIRECTORY/%")
            } else {
                // Android 9 and below: No initial selection, filter manually by path later
                selection = null // No selection to get all images
                selectionArgs = emptyArray() // No selection arguments
            }

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
                    val filenameColumn =
                        cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                    val dateModifiedColumn =
                        cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_MODIFIED)
//                    val dataColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA) // Needed for path filtering

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn)
                        val filename = cursor.getString(filenameColumn)
                        val dateModified = cursor.getLong(dateModifiedColumn)
                        val uri = ContentUris.withAppendedId(uriExternal, id)
//                        val filePath = cursor.getString(dataColumn)

                        // Attempt to load the image bitmap, might be null if loading fails
                        val imageBitmap = uri.toImageBitmap(context)

                        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                            // For Android 9 and below, manually filter by path
//                            if (filePath.contains(DIRECTORY)) {
                            result.add(DocImg(id, filename, imageBitmap, uri, dateModified))
//                            }
                        } else {
                            // For Android 10 and above, use the result directly
                            result.add(DocImg(id, filename, imageBitmap, uri, dateModified))
                        }
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
        return withContext(Dispatchers.IO) {
            try {
                // Determine where to save the file based on the Android version
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    // Android 10 and above: Use MediaStore and RELATIVE_PATH
                    saveImageUsingMediaStore(imageUri)
                } else {
                    // Android 9 and below: Use traditional file path
                    saveImageUsingFile(imageUri)
                }
            } catch (e: Exception) {
                Result.Failed(DataError.Storage.ERROR_SAVING)
            }
        }
    }

    private suspend fun saveImageUsingMediaStore(imageUri: Uri): EmptyResult<DataError.Storage> {
        val contentValues = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, "DocScan_${System.currentTimeMillis()}")
            put(MediaStore.MediaColumns.MIME_TYPE, "image/png")
            put(
                MediaStore.MediaColumns.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/$DIRECTORY"
            )
        }

        return try {
            val destinationUri = context.contentResolver.insert(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                contentValues
            ) ?: return Result.Failed(DataError.Storage.ERROR_SAVING)

            context.contentResolver.openOutputStream(destinationUri)?.use { outputStream ->
                val bitmap = imageUri.toBitmap(context)
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                outputStream.flush()
                Result.Success(Unit).asEmptyDataResult()
            } ?: Result.Failed(DataError.Storage.ERROR_SAVING)
        } catch (e: Exception) {
            Result.Failed(DataError.Storage.ERROR_SAVING)
        }
    }

    private suspend fun saveImageUsingFile(imageUri: Uri): EmptyResult<DataError.Storage> {
        // Define the directory where the image will be saved
        val directory = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            DIRECTORY
        )
        if (!directory.exists()) {
            if (!directory.mkdirs()) {
                return Result.Failed(DataError.Storage.ERROR_SAVING)
            }
        }

        // Create a new file for the image
        val fileName = "DocScan_${System.currentTimeMillis()}.png"
        val file = File(directory, fileName)

        return try {
            // Open output stream to write the image
            withContext(Dispatchers.IO) {
                FileOutputStream(file).use { outputStream ->
                    val bitmap = imageUri.toBitmap(context)
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
                    outputStream.flush()
                }
            }
            // Notify MediaStore about the new image file
            context.sendBroadcast(Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE, Uri.fromFile(file)))
            Result.Success(Unit).asEmptyDataResult()
        } catch (e: Exception) {
            Result.Failed(DataError.Storage.ERROR_SAVING)
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
