package com.fictioncutshort.justacalculator.ui.components

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat

/**
 * The set of runtime permissions the game uses across the various phases:
 *   - CAMERA: surveillance room (Door 4) live tunnel, Phase-1 phone camera app.
 *   - RECORD_AUDIO: talk handler, Phase-1 phone mic gags.
 *   - ACCESS_FINE_LOCATION: location-based narrative beats.
 *   - READ_CONTACTS: Phase-1 contacts app, narrative beats.
 *   - POST_NOTIFICATIONS (Android 13+): scheduled in-app notifications.
 *
 * Building this list dynamically (rather than as a constant) keeps the
 * POST_NOTIFICATIONS entry off of pre-Tiramisu devices where the runtime
 * permission doesn't exist.
 */
internal fun gamePermissions(): List<String> = buildList {
    add(Manifest.permission.CAMERA)
    add(Manifest.permission.RECORD_AUDIO)
    add(Manifest.permission.ACCESS_FINE_LOCATION)
    add(Manifest.permission.READ_CONTACTS)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        add(Manifest.permission.POST_NOTIFICATIONS)
    }
}

/**
 * On first composition, finds any of [permissions] that aren't currently
 * granted and fires a single multi-permission system dialog for them. Subsequent
 * results are reported via [onResult]; the map only contains entries for the
 * permissions that were actually requested in this round (Android's contract
 * behavior), so any caller tracking specific permissions should treat a missing
 * key as "still in the previous state".
 *
 * Use at the entry of any major game scene where the player might have denied
 * or auto-revoked a permission since the narrative beat that originally
 * requested it — re-entering the scene gives them a fresh chance to grant.
 *
 * Note: if a permission has been permanently denied (two denials on Android 11+
 * or "don't ask again" on older versions) the dialog will not appear; Android
 * silently returns DENIED. That's a platform limitation and requires the user
 * to re-grant from system settings.
 */
@Composable
fun RequestGamePermissionsOnEntry(
    permissions: List<String> = gamePermissions(),
    onResult: (Map<String, Boolean>) -> Unit = {},
) {
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results -> onResult(results) }
    LaunchedEffect(Unit) {
        val missing = permissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }
        if (missing.isNotEmpty()) launcher.launch(missing.toTypedArray())
    }
}
