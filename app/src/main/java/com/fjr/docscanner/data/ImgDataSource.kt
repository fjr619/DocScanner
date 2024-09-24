package com.fjr.docscanner.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import com.fjr.docscanner.data.util.DIRECTORY
import com.fjr.docscanner.data.util.toBitmap
import com.fjr.docscanner.data.util.toImageBitmap
import com.fjr.docscanner.domain.model.DocImg
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
class ImgDataSource(
    val context: Context,
) {
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
}