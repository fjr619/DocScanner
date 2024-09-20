package com.fjr.docscanner.data.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

//fun Uri.toBitmap(context: Context, defaultBitmap: Bitmap? = null): Bitmap {
//    return try {
//        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
//            // Use ImageDecoder for API 28 and above
//            ImageDecoder.decodeBitmap(
//                ImageDecoder.createSource(context.contentResolver, this)
//            ) ?: defaultBitmap ?: createDefaultBitmap()
//        } else {
//            // Use BitmapFactory for API levels below 28
//            context.contentResolver.openInputStream(this)?.use { inputStream ->
//                BitmapFactory.decodeStream(inputStream)
//            } ?: defaultBitmap ?: createDefaultBitmap()
//        }
//    } catch (e: Exception) {
//        defaultBitmap ?: createDefaultBitmap()
//    }
//}

suspend fun Uri.toBitmap(context: Context): Bitmap {
    return withContext(Dispatchers.IO) {
        try {
            // Create an ImageLoader with Coil
            val imageLoader = ImageLoader(context)

            // Create an ImageRequest for the given Uri
            val request = ImageRequest.Builder(context)
                .data(this@toBitmap) // Uri to be loaded
                .allowHardware(false) // Disable hardware bitmaps to ensure we get a Bitmap result
                .build()

            // Execute the request and get the result
            val result = imageLoader.execute(request)

            // If successful, return the Bitmap, else return the default bitmap
            if (result is SuccessResult) {
                val drawable = result.drawable
                if (drawable is BitmapDrawable) {
                    drawable.bitmap
                } else {
                    drawable.toBitmap() // Convert other types of drawables to bitmap
                }
            } else {
                createDefaultBitmap()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            createDefaultBitmap()
        }
    }
}

// Function to create a simple default bitmap
private fun createDefaultBitmap(): Bitmap {
    val width = 200
    val height = 200
    val defaultBitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(defaultBitmap)
    val paint = Paint()
    paint.color = Color.LTGRAY
    canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    paint.color = Color.RED
    paint.textSize = 40f
    canvas.drawText("No Image", 20f, height / 2f, paint)
    return defaultBitmap
}


suspend fun Uri.toImageBitmap(context: Context): ImageBitmap {
    return toBitmap(context).asImageBitmap()
}
