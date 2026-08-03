package com.fictioncutshort.justacalculator.platform

import android.content.ContentValues
import android.graphics.Bitmap
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

actual fun saveImageToGallery(fileName: String, image: ImageBitmap): Boolean = try {
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, fileName)
        put(MediaStore.Images.Media.MIME_TYPE, "image/png")
        if (Build.VERSION.SDK_INT >= 29) {
            put(
                MediaStore.Images.Media.RELATIVE_PATH,
                Environment.DIRECTORY_PICTURES + "/JustACalculator",
            )
            // Hides the row until the bytes are there, so the gallery never
            // shows a half-written image.
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
    }
    val resolver = AppInit.context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    if (uri == null) {
        false
    } else {
        val wrote = resolver.openOutputStream(uri)?.use {
            image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
        } ?: false
        if (wrote && Build.VERSION.SDK_INT >= 29) {
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        }
        wrote
    }
} catch (_: Exception) {
    false
}

actual fun showToast(message: String) {
    Toast.makeText(AppInit.context, message, Toast.LENGTH_SHORT).show()
}

private val dateTimeFormat by lazy { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }

actual fun formatDateTime(millis: Long): String = dateTimeFormat.format(Date(millis))
