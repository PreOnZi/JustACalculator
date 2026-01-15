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
 */
fun createSecretFile(context: Context) {
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
            val contentValues = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, "FCS_JustAC_ConsoleAds.txt")
                put(MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(MediaStore.Downloads.IS_PENDING, 1)
            }

            val resolver = context.contentResolver
            val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

            uri?.let {
                resolver.openOutputStream(it)?.use { outputStream ->
                    outputStream.write(content.toByteArray())
                }
                // Mark file as complete
                contentValues.clear()
                contentValues.put(MediaStore.Downloads.IS_PENDING, 0)
                resolver.update(uri, contentValues, null, null)
            }
        } else {
            // Android 9 and below - direct file access
            @Suppress("DEPRECATION")
            val downloadsDir = Environment.getExternalStoragePublicDirectory(
                Environment.DIRECTORY_DOWNLOADS
            )
            val file = File(downloadsDir, "FCS_JustAC_ConsoleAds.txt")
            file.writeText(content)
        }
        Log.d("Calculator", "Secret file created successfully")
    } catch (e: Exception) {
        Log.e("Calculator", "Failed to create secret file", e)
    }
}