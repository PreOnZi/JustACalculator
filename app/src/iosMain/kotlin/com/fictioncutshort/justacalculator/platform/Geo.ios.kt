package com.fictioncutshort.justacalculator.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import platform.CoreLocation.CLLocation
import platform.CoreLocation.CLLocationManager
import platform.CoreLocation.CLLocationManagerDelegateProtocol
import platform.CoreLocation.kCLLocationAccuracyBest
import platform.Foundation.NSError
import platform.Foundation.timeIntervalSince1970
import platform.darwin.NSObject
import platform.darwin.dispatch_async
import platform.darwin.dispatch_get_main_queue
import kotlin.math.roundToLong

@OptIn(ExperimentalForeignApi::class)
private fun CLLocation.toFix(): LocationFix {
    val coord = coordinate.useContents { latitude to longitude }
    return LocationFix(
        latitude = coord.first,
        longitude = coord.second,
        altitude = altitude,
        // Core Location reports negative accuracy to mean "invalid".
        accuracyM = if (horizontalAccuracy >= 0) horizontalAccuracy.toFloat() else 0f,
        timeMs = (timestamp.timeIntervalSince1970 * 1000.0).roundToLong(),
    )
}

/**
 * Core Location has no notion of a minimum interval — it filters by distance
 * only — so [minIntervalMs] is unused here. The caller's own quality filter
 * absorbs the difference, and dropping fixes on a timer would only make it
 * slower to settle.
 */
@OptIn(ExperimentalForeignApi::class)
actual fun startLocationUpdates(
    minIntervalMs: Long,
    minDistanceM: Float,
    onFix: (LocationFix) -> Unit,
): LocationUpdates? {
    if (!hasPermission(AppInit.context, AppPermission.LOCATION)) return null
    if (!CLLocationManager.locationServicesEnabled()) return null

    val delegate = object : NSObject(), CLLocationManagerDelegateProtocol {
        override fun locationManager(manager: CLLocationManager, didUpdateLocations: List<*>) {
            for (loc in didUpdateLocations) {
                (loc as? CLLocation)?.let { onFix(it.toFix()) }
            }
        }

        override fun locationManager(manager: CLLocationManager, didFailWithError: NSError) {
            // A transient failure is not fatal; updates continue.
        }
    }

    // The manager must be created and driven on the main thread, and retained
    // for as long as it is delivering — the delegate is held weakly.
    val manager = CLLocationManager()
    manager.delegate = delegate
    manager.desiredAccuracy = kCLLocationAccuracyBest
    manager.distanceFilter = minDistanceM.toDouble()

    dispatch_async(dispatch_get_main_queue()) {
        manager.startUpdatingLocation()
        // Seed from the cached fix the way Android's getLastKnownLocation does.
        manager.location?.let { onFix(it.toFix()) }
    }

    return object : LocationUpdates {
        // Captured so neither is collected while updates are running.
        private val held = manager to delegate
        override fun stop() {
            dispatch_async(dispatch_get_main_queue()) {
                held.first.stopUpdatingLocation()
                held.first.delegate = null
            }
        }
    }
}
