package com.fictioncutshort.justacalculator.platform

import android.content.ContentUris
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

actual fun capturedImagePaths(max: Int): List<String> {
    val out = mutableListOf<String>()
    val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
    } else {
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    }
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.DISPLAY_NAME,
    )
    val (selection, args) = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        // RELATIVE_PATH is the directory under DCIM/Pictures/Movies/etc.
        "${MediaStore.Images.Media.RELATIVE_PATH} LIKE ?" to arrayOf("%DCIM/JustACalculator%")
    } else {
        "${MediaStore.Images.Media.DISPLAY_NAME} LIKE ?" to arrayOf("calculator_%")
    }
    val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

    try {
        AppInit.context.contentResolver
            .query(collection, projection, selection, args, sortOrder)?.use { c ->
                val idCol = c.getColumnIndex(MediaStore.Images.Media._ID)
                if (idCol < 0) return@use
                while (c.moveToNext() && out.size < max) {
                    out += ContentUris.withAppendedId(collection, c.getLong(idCol)).toString()
                }
            }
    } catch (_: SecurityException) {
        // No media-images permission — return what we have (likely empty).
    }
    return out
}
