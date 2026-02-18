package com.fictioncutshort.justacalculator.util

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.content.ContentUris
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import androidx.annotation.RequiresApi
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

private const val TAG = "Calculator"
private const val FILE_NAME = "FCS_JustAC_ConsoleAds.txt"

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
╔═══════════════════════════════════════════════════════════════
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
╚═══════════════════════════════════════════════════════════════
    """.trimIndent()

    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        createSecretFileMediaStore(context, content)
    } else {
        createSecretFileLegacy(content)
    }
}

/**
 * Android 10+ (Q): Uses MediaStore API with scoped storage.
 *
 * Strategy:
 * 1. Query for any existing entry (including pending ones from previous attempts/installs)
 * 2. If found and writable → overwrite it
 * 3. If found but not writable (from different app) → delete won't work, insert with unique name
 * 4. If not found → fresh insert
 * 5. Fallback to app-private external dir if all MediaStore attempts fail
 */
@RequiresApi(Build.VERSION_CODES.Q)
private fun createSecretFileMediaStore(context: Context, content: String): Boolean {
    val resolver = context.contentResolver

    // ── Step 1: Clean up any pending/stale entries we own ──────────────────
    // This handles the case where a previous attempt left an IS_PENDING=1 orphan.
    // We query broadly (including pending) so we can clean up our own entries.
    try {
        val projection = arrayOf(MediaStore.Downloads._ID, MediaStore.Downloads.IS_PENDING)
        val selection = "${MediaStore.Downloads.DISPLAY_NAME} = ?"
        val selectionArgs = arrayOf(FILE_NAME)

        // Include pending files in the query (Android 10+)
        val bundle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
                putInt(MediaStore.QUERY_ARG_MATCH_PENDING, MediaStore.MATCH_INCLUDE)
            }
        } else null

        val cursor = if (bundle != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            resolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection, bundle, null)
        } else {
            resolver.query(MediaStore.Downloads.EXTERNAL_CONTENT_URI, projection, selection, selectionArgs, null)
        }

        cursor?.use {
            while (it.moveToNext()) {
                val id = it.getLong(it.getColumnIndexOrThrow(MediaStore.Downloads._ID))
                val entryUri = ContentUris.withAppendedId(
                    MediaStore.Downloads.EXTERNAL_CONTENT_URI, id
                )
                try {
                    resolver.delete(entryUri, null, null)
                    Log.d(TAG, "Deleted existing file entry: $entryUri")
                } catch (e: Exception) {
                    // Entry belongs to a different app - can't delete, ignore
                    Log.d(TAG, "Could not delete existing entry (different owner): ${e.message}")
                }
            }
        }
    } catch (e: Exception) {
        Log.d(TAG, "Cleanup query failed (non-critical): ${e.message}")
    }

    // ── Step 2: Insert fresh entry ──────────────────────────────────────────
    val contentValues = ContentValues().apply {
        put(MediaStore.Downloads.DISPLAY_NAME, FILE_NAME)
        put(MediaStore.Downloads.MIME_TYPE, "text/plain")
        put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
        put(MediaStore.Downloads.IS_PENDING, 1)
    }

    return try {
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

        if (uri == null) {
            Log.e(TAG, "MediaStore insert returned null URI")
            return createSecretFileFallback(context, content)
        }

        // Write content
        val written = try {
            resolver.openOutputStream(uri, "wt")?.use { stream ->
                stream.write(content.toByteArray(Charsets.UTF_8))
                stream.flush()
                true
            } ?: false
        } catch (e: Exception) {
            Log.e(TAG, "openOutputStream failed: ${e.message}")
            false
        }

        if (!written) {
            // Clean up the dangling pending entry
            try { resolver.delete(uri, null, null) } catch (_: Exception) {}
            Log.e(TAG, "Failed to write content, trying fallback")
            return createSecretFileFallback(context, content)
        }

        // Mark as published
        val publishValues = ContentValues().apply {
            put(MediaStore.Downloads.IS_PENDING, 0)
        }
        try {
            resolver.update(uri, publishValues, null, null)
        } catch (e: Exception) {
            // Some devices throw here but the file is still readable - log and continue
            Log.w(TAG, "IS_PENDING update failed (file may still be OK): ${e.message}")
        }

        Log.d(TAG, "Secret file created successfully via MediaStore: $uri")
        true

    } catch (e: Exception) {
        Log.e(TAG, "MediaStore creation failed: ${e.message}", e)
        createSecretFileFallback(context, content)
    }
}

/**
 * Android 9 and below: Direct file access (no scoped storage).
 */
@Suppress("DEPRECATION")
private fun createSecretFileLegacy(content: String): Boolean {
    return try {
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        if (!downloadsDir.exists()) downloadsDir.mkdirs()
        val file = File(downloadsDir, FILE_NAME)
        file.writeText(content, Charsets.UTF_8)
        Log.d(TAG, "Secret file created (legacy) at: ${file.absolutePath}")
        true
    } catch (e: Exception) {
        Log.e(TAG, "Legacy file creation failed: ${e.message}", e)
        false
    }
}

/**
 * Last-resort fallback: saves to the app's own external files directory.
 *
 * NOTE: This location won't appear in the system Downloads app, so the
 * calculator's message is updated in Browsereffects.kt to show the code
 * inline if file creation returns false. This path shouldn't be reached
 * on normal devices but handles edge cases gracefully.
 */
private fun createSecretFileFallback(context: Context, content: String): Boolean {
    return try {
        val externalDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: context.filesDir  // absolute last resort: internal storage
        externalDir.mkdirs()
        val file = File(externalDir, FILE_NAME)
        file.writeText(content, Charsets.UTF_8)
        Log.w(TAG, "Secret file saved to app-private dir (won't show in Downloads): ${file.absolutePath}")
        // Return false so the caller can show the inline fallback message instead
        false
    } catch (e: Exception) {
        Log.e(TAG, "Fallback file creation also failed: ${e.message}", e)
        false
    }
}