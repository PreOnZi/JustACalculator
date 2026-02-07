package com.fictioncutshort.justacalculator.util

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import java.io.File

/**
 * FileCreation.kt
 *
 * Creates the secret text file in the Downloads folder that contains
 * the console access code. This is part of the story puzzle at step 112.
 *
 * The file is created when the user agrees to "allow the calculator to
 * look around" - it's disguised as a system configuration file.
 */

/**
 * Creates the secret instructions file in the user's Downloads folder.
 *
 * The file contains:
 * - Console access code: 353942320485
 * - Admin code: 12340
 * - Navigation instructions for disabling ads
 *
 * @param context Android context
 * @return true if file was created successfully, false otherwise
 */
fun createSecretFile(context: Context): Boolean {
    val content = """
═══════════════════════════════════════════════════════════════
Console Advertising Setting for verion 1.0

- Administrator permission required
- Any issues to be reported directly to the supervising manager
- Do not disable advertising on consumer-ready versions!
- All forms of advertising must be enabled once testing is done to maintain stability
- Please ensure the versions of this manual and of your build correspond

Open console:
Enter the console code: 353942320485 and confirm (++)

Once in the console, navigate to Administrator settings (2++)
Enter the administrator code (12340 [must be changed before launch!]) when prompted
Go to: Connectivity settings (4++)
Select: 2(++) for Promotion & advertising options
Select: Disable banner advertising (2++)


Navigation:
- 88++ = Go back
- 99++ = Exit console

Remember to return everything to default setting once done with testing.
Any issues to be reported to management (we are aware of the full-screen ad issues and unreliability).

FCS
FictionCutShort
═══════════════════════════════════════════════════════════════
    """.trimIndent()

    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Android 10+ uses MediaStore API (scoped storage)

            // First, try to delete any existing file with the same name
            try {
                val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ?"
                val selectionArgs = arrayOf("FCS_JustAC_ConsoleAds.txt")
                context.contentResolver.delete(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                    selection,
                    selectionArgs
                )
            } catch (e: Exception) {
                Log.d("Calculator", "No existing file to delete or delete failed: ${e.message}")
            }

            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, "FCS_JustAC_ConsoleAds.txt")
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

            if (uri == null) {
                Log.e("Calculator", "MediaStore insert returned null URI - trying alternative method")
                return createSecretFileAlternative(context, content)
            }

            val outputStream = resolver.openOutputStream(uri)
            if (outputStream == null) {
                Log.e("Calculator", "Could not open output stream for URI: $uri")
                // Clean up the pending entry
                resolver.delete(uri, null, null)
                return createSecretFileAlternative(context, content)
            }

            outputStream.use { stream ->
                stream.write(content.toByteArray())
            }

            // Mark file as complete
            contentValues.clear()
            contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
            val updated = resolver.update(uri, contentValues, null, null)

            if (updated == 0) {
                Log.w("Calculator", "Failed to mark file as complete, but file may still be accessible")
            }

            Log.d("Calculator", "Secret file created successfully via MediaStore at: $uri")
            return true
        } else {
            // Android 9 and below - direct file access
            @Suppress("DEPRECATION")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            )
            if (!downloadsDir.exists()) {
                downloadsDir.mkdirs()
            }
            val file = File(downloadsDir, "FCS_JustAC_ConsoleAds.txt")
            file.writeText(content)
            Log.d("Calculator", "Secret file created successfully at: ${file.absolutePath}")
            return true
        }
    } catch (e: Exception) {
        Log.e("Calculator", "Failed to create secret file: ${e.message}", e)
        // Try alternative method as fallback
        return try {
            createSecretFileAlternative(context, content)
        } catch (e2: Exception) {
            Log.e("Calculator", "Alternative method also failed: ${e2.message}", e2)
            false
        }
    }
}

/**
 * Alternative method to create the file using app-specific external storage
 * and then copying to Downloads via MediaStore with different approach
 */
private fun createSecretFileAlternative(context: Context, content: String): Boolean {
    return try {
        // Try using RELATIVE_PATH which works better on some Android versions
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, "FCS_JustAC_ConsoleAds.txt")
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

            if (uri != null) {
                resolver.openOutputStream(uri)?.use { stream ->
                    stream.write(content.toByteArray())
                }
                Log.d("Calculator", "Secret file created via alternative method at: $uri")
                return true
            }
        }

        // Last resort: save to app's external files directory
        val externalDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
        if (externalDir != null) {
            val file = File(externalDir, "FCS_JustAC_ConsoleAds.txt")
            file.writeText(content)
            Log.d("Calculator", "Secret file created in app external storage: ${file.absolutePath}")
            // Note: This won't show in Downloads app, but file exists
            return true
        }

        false
    } catch (e: Exception) {
        Log.e("Calculator", "Alternative file creation failed: ${e.message}", e)
        false
    }
}