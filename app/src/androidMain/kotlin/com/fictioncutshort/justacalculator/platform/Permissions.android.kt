package com.fictioncutshort.justacalculator.platform

import android.Manifest
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat

actual fun hasPermission(context: AppContext, permission: AppPermission): Boolean {
    val name = when (permission) {
        AppPermission.MICROPHONE -> Manifest.permission.RECORD_AUDIO
        AppPermission.LOCATION -> Manifest.permission.ACCESS_FINE_LOCATION
        AppPermission.CONTACTS -> Manifest.permission.READ_CONTACTS
        AppPermission.CAMERA -> Manifest.permission.CAMERA
    }
    return ContextCompat.checkSelfPermission(context, name) == PackageManager.PERMISSION_GRANTED
}
