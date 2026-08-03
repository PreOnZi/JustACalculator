package com.fictioncutshort.justacalculator.platform

import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle

private fun Location.toFix() = LocationFix(
    latitude = latitude,
    longitude = longitude,
    altitude = altitude,
    accuracyM = if (hasAccuracy()) accuracy else 0f,
    timeMs = time,
)

actual fun startLocationUpdates(
    minIntervalMs: Long,
    minDistanceM: Float,
    onFix: (LocationFix) -> Unit,
): LocationUpdates? {
    if (!hasPermission(AppInit.context, AppPermission.LOCATION)) return null
    val lm = AppInit.context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
        ?: return null

    val listener = object : LocationListener {
        override fun onLocationChanged(loc: Location) = onFix(loc.toFix())
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {}
        override fun onProviderDisabled(provider: String) {}
    }

    return try {
        // Seed from whatever is cached, then subscribe to both providers. The
        // caller filters, so a stale network fix cannot win over a fresh GPS one.
        lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)?.let { onFix(it.toFix()) }
        lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)?.let { onFix(it.toFix()) }
        if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            lm.requestLocationUpdates(
                LocationManager.GPS_PROVIDER, minIntervalMs, minDistanceM, listener,
            )
        }
        if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
            lm.requestLocationUpdates(
                LocationManager.NETWORK_PROVIDER, minIntervalMs, minDistanceM, listener,
            )
        }
        object : LocationUpdates {
            override fun stop() = lm.removeUpdates(listener)
        }
    } catch (_: SecurityException) {
        // Permission revoked mid-flight.
        null
    }
}
