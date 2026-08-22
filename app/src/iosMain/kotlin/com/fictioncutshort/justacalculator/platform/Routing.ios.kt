package com.fictioncutshort.justacalculator.platform

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.coroutines.suspendCancellableCoroutine
import platform.CoreLocation.CLLocationCoordinate2D
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.Foundation.NSMakeRange
import platform.MapKit.MKDirections
import platform.MapKit.MKDirectionsRequest
import platform.MapKit.requestsAlternateRoutes
import platform.MapKit.transportType
import platform.MapKit.MKDirectionsTransportTypeWalking
import platform.MapKit.MKMapItem
import platform.MapKit.MKPlacemark
import platform.MapKit.MKPolyline
import platform.MapKit.MKRoute
import kotlin.coroutines.resume

/**
 * A walking route from MapKit.
 *
 * Free, keyless, and no new privacy surface: Building 5's map on iOS is an
 * MKMapView, so Apple is already receiving the coordinates it draws.
 *
 * Every failure path returns null rather than throwing — no route, no network,
 * a rate-limited response, a destination MapKit cannot walk to. The caller
 * treats null as "draw the dashed curve instead", which is exactly what Android
 * does on every call, so a failure here degrades to the behaviour that ships on
 * the other platform rather than to an error.
 */
@OptIn(ExperimentalForeignApi::class)
actual suspend fun platformWalkingRoute(from: GeoPoint, to: GeoPoint): List<GeoPoint>? =
    suspendCancellableCoroutine { cont ->
        val request = MKDirectionsRequest().apply {
            setSource(MKMapItem(MKPlacemark(CLLocationCoordinate2DMake(from.latitude, from.longitude))))
            setDestination(MKMapItem(MKPlacemark(CLLocationCoordinate2DMake(to.latitude, to.longitude))))
            transportType = MKDirectionsTransportTypeWalking
            // One route is all the map draws; asking for alternates would only
            // cost time and quota.
            requestsAlternateRoutes = false
        }
        val directions = MKDirections(request)

        // MapKit calls back on the main queue. resume is guarded because a
        // cancelled coroutine (the player leaving the chapter mid-request) would
        // otherwise resume a continuation that is already gone.
        directions.calculateDirectionsWithCompletionHandler { response, _ ->
            if (!cont.isActive) return@calculateDirectionsWithCompletionHandler
            val route = (response?.routes?.firstOrNull() as? MKRoute)
            cont.resume(route?.polyline?.toGeoPoints()?.takeIf { it.isNotEmpty() })
        }

        cont.invokeOnCancellation { directions.cancel() }
    }

/**
 * MKPolyline stores its points in a C array, so they have to be copied out
 * rather than iterated — `getCoordinates:range:` fills a buffer the caller owns.
 */
@OptIn(ExperimentalForeignApi::class)
private fun MKPolyline.toGeoPoints(): List<GeoPoint> {
    val n = pointCount.toInt()
    if (n <= 0) return emptyList()
    return memScoped {
        val buffer = allocArray<CLLocationCoordinate2D>(n)
        getCoordinates(buffer, NSMakeRange(0u, n.toULong()))
        List(n) { i ->
            val c = buffer[i]
            GeoPoint(c.latitude, c.longitude)
        }
    }
}
