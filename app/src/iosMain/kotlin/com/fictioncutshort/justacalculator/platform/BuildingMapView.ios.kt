package com.fictioncutshort.justacalculator.platform

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.interop.UIKitView
import kotlinx.cinterop.CValue
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.useContents
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import platform.CoreGraphics.CGContextAddLineToPoint
import platform.CoreGraphics.CGContextFillEllipseInRect
import platform.CoreGraphics.CGContextMoveToPoint
import platform.CoreGraphics.CGContextSetLineCap
import platform.CoreGraphics.CGContextSetLineWidth
import platform.CoreGraphics.CGContextSetStrokeColorWithColor
import platform.CoreGraphics.CGContextSetFillColorWithColor
import platform.CoreGraphics.CGContextStrokePath
import platform.CoreGraphics.CGLineCap
import platform.CoreGraphics.CGRectMake
import platform.CoreGraphics.CGSizeMake
import platform.CoreLocation.CLLocationCoordinate2DMake
import platform.CoreLocation.CLLocationCoordinate2D
import platform.MapKit.MKAnnotationProtocol
import platform.MapKit.MKAnnotationView
import platform.MapKit.MKCircle
import platform.MapKit.MKCircleRenderer
import platform.MapKit.MKCoordinateRegionMakeWithDistance
import platform.MapKit.MKMapView
import platform.MapKit.MKMapViewDelegateProtocol
import platform.MapKit.MKOverlayProtocol
import platform.MapKit.MKOverlayRenderer
import platform.MapKit.MKPointAnnotation
import platform.MapKit.MKPolyline
import platform.MapKit.MKPolylineRenderer
import platform.MapKit.MKUserTrackingModeNone
import platform.MapKit.addOverlay
import platform.MapKit.removeOverlay
import platform.QuartzCore.CATransaction
import platform.UIKit.UIColor
import platform.UIKit.UIGraphicsBeginImageContextWithOptions
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetCurrentContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIUserInterfaceStyle
import platform.darwin.NSObject
import kotlin.math.max

/**
 * MapKit counterpart to the osmdroid map.
 *
 * The Android build inverts OSM's light raster tiles to get the console look;
 * MapKit has no colour filter, so the equivalent is forcing the view's dark
 * appearance — which gives the same dark map with legible labels rather than a
 * photographic negative.
 */

private const val ORANGE_R = 1.0
private const val ORANGE_G = 0.4
private const val GREEN_R = 0.2
private const val GREEN_G = 1.0
private const val GREEN_B = 0.4

private const val DEST_ID = "dest"
private const val USER_ID = "user"

@OptIn(ExperimentalForeignApi::class)
private fun GeoPoint.coord(): CValue<CLLocationCoordinate2D> =
    CLLocationCoordinate2DMake(latitude, longitude)

private fun geoKey(p: GeoPoint) = "${p.latitude},${p.longitude}"

/**
 * Orange crosshair for a destination pin — square frame plus cross, matching
 * the bitmap the Android overlay draws. [dim] fades the siblings the player did
 * not pick.
 */
@OptIn(ExperimentalForeignApi::class)
private fun crosshairImage(size: Double, dim: Boolean): UIImage? {
    UIGraphicsBeginImageContextWithOptions(CGSizeMake(size, size), false, 0.0)
    val ctx = UIGraphicsGetCurrentContext()
    val alpha = if (dim) 0.37 else 1.0
    val colour = UIColor.colorWithRed(ORANGE_R, ORANGE_G, 0.0, alpha).CGColor

    CGContextSetStrokeColorWithColor(ctx, colour)
    CGContextSetFillColorWithColor(ctx, colour)
    CGContextSetLineWidth(ctx, 5.0)
    CGContextSetLineCap(ctx, CGLineCap.kCGLineCapSquare)

    val cx = size / 2; val cy = size / 2
    val r1 = size * 0.42; val r2 = size * 0.30; val tick = size * 0.10
    val left = cx - r1; val right = cx + r1; val top = cy - r1; val bot = cy + r1
    val arm = r1 * 0.55

    fun line(x1: Double, y1: Double, x2: Double, y2: Double) {
        CGContextMoveToPoint(ctx, x1, y1)
        CGContextAddLineToPoint(ctx, x2, y2)
    }
    // Corner brackets.
    line(left, top, left + arm, top); line(right - arm, top, right, top)
    line(left, bot, left + arm, bot); line(right - arm, bot, right, bot)
    line(left, top, left, top + arm); line(left, bot, left, bot - arm)
    line(right, top, right, top + arm); line(right, bot, right, bot - arm)
    // Inner ticks.
    line(cx, cy - r2, cx, cy - r2 + tick * 2); line(cx, cy + r2, cx, cy + r2 - tick * 2)
    line(cx - r2, cy, cx - r2 + tick * 2, cy); line(cx + r2, cy, cx + r2 - tick * 2, cy)
    CGContextStrokePath(ctx)

    CGContextFillEllipseInRect(ctx, CGRectMake(cx - 5.0, cy - 5.0, 10.0, 10.0))

    val image = UIGraphicsGetImageFromCurrentImageContext()
    UIGraphicsEndImageContext()
    return image
}

/** Everything the update pass needs to diff against last time. */
private class MapState {
    var mapView: MKMapView? = null
    var delegate: MapDelegate? = null
    val dests = mutableMapOf<String, MKPointAnnotation>()
    var user: MKPointAnnotation? = null
    var accuracy: MKCircle? = null
    var accuracyRadius: Double = -1.0
    var route: MKPolyline? = null
    var routeKey: String = ""
    var activeKey: String? = null
    var lastFit: Int = -1
    val bright by lazy { crosshairImage(54.0, dim = false) }
    val dim by lazy { crosshairImage(54.0, dim = true) }
}

@OptIn(ExperimentalForeignApi::class)
private class MapDelegate(
    private val state: MapState,
) : NSObject(), MKMapViewDelegateProtocol {

    var onDestTap: (GeoPoint) -> Unit = {}

    override fun mapView(
        mapView: MKMapView,
        viewForAnnotation: MKAnnotationProtocol,
    ): MKAnnotationView? {
        val point = viewForAnnotation as? MKPointAnnotation ?: return null
        val isUser = point.title == USER_ID

        val id = if (isUser) USER_ID else DEST_ID
        val view = mapView.dequeueReusableAnnotationViewWithIdentifier(id)
            ?: MKAnnotationView(annotation = point, reuseIdentifier = id)
        view.annotation = point
        view.canShowCallout = false

        if (isUser) {
            view.image = null
            view.backgroundColor = UIColor.colorWithRed(GREEN_R, GREEN_G, GREEN_B, 1.0)
            view.layer.cornerRadius = 7.0
            view.layer.borderWidth = 2.0
            view.layer.borderColor = UIColor.whiteColor.CGColor
            view.setFrame(CGRectMake(0.0, 0.0, 14.0, 14.0))
        } else {
            // subtitle carries the dim flag: MKPointAnnotation has nowhere else
            // to hang per-pin state, and the pin's identity is its coordinate.
            view.image = if (point.subtitle == "dim") state.dim else state.bright
        }
        return view
    }

    override fun mapView(mapView: MKMapView, didSelectAnnotationView: MKAnnotationView) {
        val point = didSelectAnnotationView.annotation as? MKPointAnnotation ?: return
        if (point.title == USER_ID) return
        point.coordinate.useContents { onDestTap(GeoPoint(latitude, longitude)) }
        // Deselect so the same pin can be tapped again.
        mapView.deselectAnnotation(point, animated = false)
    }

    override fun mapView(
        mapView: MKMapView,
        rendererForOverlay: MKOverlayProtocol,
    ): MKOverlayRenderer {
        return when (rendererForOverlay) {
            is MKPolyline -> MKPolylineRenderer(polyline = rendererForOverlay).apply {
                strokeColor = UIColor.colorWithRed(ORANGE_R, ORANGE_G, 0.0, 0.86)
                lineWidth = 5.0
                lineCap = CGLineCap.kCGLineCapRound
                lineJoin = platform.CoreGraphics.CGLineJoin.kCGLineJoinRound
                // Dashed while the route is a straight-line stand-in.
                if (state.routeKey.startsWith("direct")) lineDashPattern = listOf(11, 7)
            }
            is MKCircle -> MKCircleRenderer(circle = rendererForOverlay).apply {
                fillColor = UIColor.colorWithRed(GREEN_R, GREEN_G, GREEN_B, 0.15)
                strokeColor = UIColor.colorWithRed(GREEN_R, GREEN_G, GREEN_B, 0.35)
                lineWidth = 2.0
            }
            else -> MKOverlayRenderer(overlay = rendererForOverlay)
        }
    }
}

@OptIn(ExperimentalForeignApi::class)
@Composable
actual fun PlatformBuildingMapView(
    userLoc: GeoPoint?,
    userAccuracyM: Float,
    destPoints: List<GeoPoint>,
    activeDest: GeoPoint?,
    route: List<GeoPoint>,
    fitTrigger: Int,
    initialZoom: Double,
    onDestTap: (GeoPoint) -> Unit,
) {
    val state = remember { MapState() }

    UIKitView(
        factory = {
            val map = MKMapView()
            map.overrideUserInterfaceStyle = UIUserInterfaceStyle.UIUserInterfaceStyleDark
            map.showsUserLocation = false      // the game draws its own filtered fix
            map.userTrackingMode = MKUserTrackingModeNone
            map.showsCompass = false
            map.rotateEnabled = false
            val delegate = MapDelegate(state)
            map.delegate = delegate
            state.mapView = map
            state.delegate = delegate
            map
        },
        modifier = Modifier.fillMaxSize(),
        update = { map ->
            state.delegate?.onDestTap = onDestTap

            // ── user dot + accuracy disc ──
            val u = userLoc
            if (u != null) {
                val pin = state.user ?: MKPointAnnotation().also {
                    it.setTitle(USER_ID)
                    map.addAnnotation(it)
                    state.user = it
                }
                pin.setCoordinate(u.coord())

                val radius = userAccuracyM.toDouble()
                // Re-adding an overlay makes it flash, so only when it changed.
                if (radius > 1.0 && radius != state.accuracyRadius) {
                    state.accuracy?.let { map.removeOverlay(it) }
                    val circle = MKCircle.circleWithCenterCoordinate(u.coord(), radius)
                    map.addOverlay(circle)
                    state.accuracy = circle
                    state.accuracyRadius = radius
                }
            }

            // ── destination crosshairs ──
            val wanted = destPoints.associateBy(::geoKey)
            val activeKey = activeDest?.let(::geoKey)
            for (key in state.dests.keys.toList()) {
                if (key !in wanted) state.dests.remove(key)?.let { map.removeAnnotation(it) }
            }
            for ((key, point) in wanted) {
                // Dim only if there IS an active selection and this isn't it —
                // before the player picks, all options read identical.
                val dim = if (activeKey != null && key != activeKey) "dim" else ""
                val existing = state.dests[key]
                if (existing == null) {
                    val pin = MKPointAnnotation()
                    pin.setCoordinate(point.coord())
                    pin.setTitle(DEST_ID)
                    pin.setSubtitle(dim)
                    map.addAnnotation(pin)
                    state.dests[key] = pin
                } else if (existing.subtitle != dim) {
                    // The image comes from the annotation view, which is only
                    // rebuilt on re-add — so swap the pin to restyle it.
                    map.removeAnnotation(existing)
                    existing.setSubtitle(dim)
                    map.addAnnotation(existing)
                }
            }
            if (state.activeKey != activeKey) state.activeKey = activeKey

            // ── route ──
            val active = activeDest
            if (u != null && active != null) {
                val points = if (route.isNotEmpty()) route else listOf(u, active)
                val key = (if (route.isNotEmpty()) "route" else "direct") +
                    ":${points.size}:${geoKey(points.first())}:${geoKey(points.last())}"
                if (key != state.routeKey) {
                    state.route?.let { map.removeOverlay(it) }
                    state.routeKey = key
                    // polylineWithCoordinates copies the array, so a scoped
                    // allocation is enough — it must be a C array, not a List.
                    val line = memScoped {
                        val buffer = allocArray<CLLocationCoordinate2D>(points.size)
                        points.forEachIndexed { i, point ->
                            buffer[i].latitude = point.latitude
                            buffer[i].longitude = point.longitude
                        }
                        MKPolyline.polylineWithCoordinates(buffer, points.size.toULong())
                    }
                    map.addOverlay(line)
                    state.route = line
                }
            } else if (state.route != null) {
                map.removeOverlay(state.route!!)
                state.route = null
                state.routeKey = ""
            }

            // ── re-frame ──
            // A fixed span around the cluster's midpoint, for the same reason
            // Android avoids zoomToBoundingBox: a fit computed before the view
            // is laid out collapses to a world view.
            if (u != null && destPoints.isNotEmpty() && fitTrigger != state.lastFit) {
                state.lastFit = fitTrigger
                val all = listOf(u) + destPoints
                val centre = CLLocationCoordinate2DMake(
                    all.map { it.latitude }.average(),
                    all.map { it.longitude }.average(),
                )
                val span = max(400.0, all.maxOf { it.distanceToAsDouble(u) } * 2.6)
                CATransaction.begin()
                CATransaction.setDisableActions(true)
                map.setRegion(
                    MKCoordinateRegionMakeWithDistance(centre, span, span),
                    animated = false,
                )
                CATransaction.commit()
            }
        },
    )
}
