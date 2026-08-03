package com.fictioncutshort.justacalculator.platform

import androidx.compose.ui.graphics.ImageBitmap

/**
 * Saves [image] into the device's photo library as a PNG named [fileName],
 * returning false if it could not be written.
 *
 * The two platforms disagree about what "the gallery" is — Android inserts a
 * MediaStore row under Pictures/JustACalculator, iOS adds an asset to the photo
 * library and has no say in the folder — so only the intent is shared.
 */
expect fun saveImageToGallery(fileName: String, image: ImageBitmap): Boolean

/** A brief, self-dismissing message. Toast on Android, a small overlay on iOS. */
expect fun showToast(message: String)

/** `yyyy-MM-dd HH:mm` in the device's local time. */
expect fun formatDateTime(millis: Long): String
