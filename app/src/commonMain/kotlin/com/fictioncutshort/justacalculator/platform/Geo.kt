package com.fictioncutshort.justacalculator.platform

import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * A point on the map. Named to match osmdroid's `GeoPoint`, which the map beat
 * was written against, so its arithmetic ports unchanged.
 */
data class GeoPoint(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double = 0.0,
)

/**
 * One location fix, carrying the metadata the quality filter needs.
 *
 * [accuracyM] is 0 when the platform did not report one — the map draws the
 * uncertainty rather than asserting a precision it does not have.
 */
data class LocationFix(
    val latitude: Double,
    val longitude: Double,
    val altitude: Double,
    val accuracyM: Float,
    val timeMs: Long,
) {
    fun toGeoPoint() = GeoPoint(latitude, longitude, altitude)
}

/** A running subscription to location updates. */
interface LocationUpdates {
    fun stop()
}

/**
 * Streams fixes to [onFix] until stopped, starting with any cached fix.
 *
 * Both platforms may deliver fixes that are worse than one already in hand —
 * Android because it merges two providers, iOS because Core Location emits a
 * coarse fix first and refines it — so the caller filters rather than trusting
 * arrival order.
 *
 * Returns null when permission is missing or no provider is enabled.
 */
expect fun startLocationUpdates(
    minIntervalMs: Long,
    minDistanceM: Float,
    onFix: (LocationFix) -> Unit,
): LocationUpdates?

// ─────────────────────────────────────────────────────────────────────────────
// GEODESY
//
// Replaces the two osmdroid GeoPoint methods the map beat uses. Both assume a
// sphere of osmdroid's radius, so distances agree with what the Android build
// has always computed — the round's 80–120 m destination band is tuned to it.
// ─────────────────────────────────────────────────────────────────────────────

private const val EARTH_RADIUS_M = 6378137.0
private const val DEG = PI / 180.0

/** Great-circle distance in metres, by the haversine formula. */
fun GeoPoint.distanceToAsDouble(other: GeoPoint): Double {
    val lat1 = latitude * DEG
    val lat2 = other.latitude * DEG
    val dLat = (other.latitude - latitude) * DEG
    val dLon = (other.longitude - longitude) * DEG
    val a = sin(dLat / 2).pow(2) + cos(lat1) * cos(lat2) * sin(dLon / 2).pow(2)
    return 2 * EARTH_RADIUS_M * asin(min(1.0, sqrt(a)))
}

/** The point [meters] away along [bearingDeg], measured clockwise from north. */
fun GeoPoint.destinationPoint(meters: Double, bearingDeg: Double): GeoPoint {
    val angular = meters / EARTH_RADIUS_M
    val bearing = bearingDeg * DEG
    val lat1 = latitude * DEG
    val lon1 = longitude * DEG

    val lat2 = asin(sin(lat1) * cos(angular) + cos(lat1) * sin(angular) * cos(bearing))
    val lon2 = lon1 + atan2(
        sin(bearing) * sin(angular) * cos(lat1),
        cos(angular) - sin(lat1) * sin(lat2),
    )
    // Normalise into (-180, 180] so a route crossing the antimeridian does not
    // produce a longitude the map will refuse.
    val lonDeg = ((lon2 / DEG) + 540.0) % 360.0 - 180.0
    return GeoPoint(lat2 / DEG, lonDeg, altitude)
}
