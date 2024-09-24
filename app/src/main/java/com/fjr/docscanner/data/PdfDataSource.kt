package com.fjr.docscanner.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.annotation.RequiresApi
import com.fjr.docscanner.data.util.DIRECTORY
import com.fjr.docscanner.domain.model.DocPdf
import com.fjr.docscanner.domain.util.DataError
import com.fjr.docscanner.domain.util.EmptyResult
import com.fjr.docscanner.domain.util.Result
import com.fjr.docscanner.domain.util.asEmptyDataResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.koin.core.annotation.Single
import java.io.File
import java.io.FileOutputStream

@Single
class PdfDataSource(
    val context: Context,
) {


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
}