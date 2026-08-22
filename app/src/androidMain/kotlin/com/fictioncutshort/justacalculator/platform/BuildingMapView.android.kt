package com.fictioncutshort.justacalculator.platform

import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Point
import android.graphics.drawable.BitmapDrawable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint as OsmGeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay
import kotlin.math.hypot

private fun GeoPoint.osm() = OsmGeoPoint(latitude, longitude, altitude)

@Composable
actual fun PlatformBuildingMapView(
    userLoc: GeoPoint?,
    userAccuracyM: Float,
    destPoints: List<GeoPoint>,
    activeDest: GeoPoint?,
    route: List<GeoPoint>,
    routeIsGuess: Boolean,
    routeArrow: List<GeoPoint>,
    fitTrigger: Int,
    initialZoom: Double,
    onDestTap: (GeoPoint) -> Unit,
) {
    val refs = remember { MapRefs() }

    AndroidView(
        factory = { ctx ->
            // osmdroid needs configuring before a MapView is built, and nothing
            // in the app was doing it. Two consequences, both of which look
            // exactly like what was on screen — overlays drawing fine over an
            // empty background:
            //
            //  - No user agent. osmdroid defaults to its own library name, and
            //    tile.openstreetmap.org rejects that outright under its usage
            //    policy, so every tile request comes back refused.
            //  - No osmdroid base path, so the tile cache has nowhere to live
            //    and nothing can be stored or re-read.
            //
            // The identifier must be something attributable; the application id
            // is the conventional choice.
            Configuration.getInstance().apply {
                if (userAgentValue.isNullOrBlank() || userAgentValue == "osmdroid") {
                    userAgentValue = ctx.packageName
                }
                osmdroidBasePath = ctx.filesDir.resolve("osmdroid").apply { mkdirs() }
                osmdroidTileCache = osmdroidBasePath.resolve("tiles").apply { mkdirs() }
            }
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                overlayManager.tilesOverlay.setColorFilter(TilesOverlay.INVERT_COLORS)
                setMultiTouchControls(true)
                isHorizontalMapRepetitionEnabled = false
                isVerticalMapRepetitionEnabled = false
                controller.setZoom(initialZoom)
                userLoc?.let { controller.setCenter(it.osm()) }
            }
        },
        modifier = Modifier.fillMaxSize(),
        update = { mapView ->
            // Crosshair bitmaps — built once and reused. Two variants: full
            // brightness for the active (or pre-selection) dests, and faded
            // for the un-selected siblings once the player has picked one.
            if (refs.brightBitmap == null) {
                refs.brightBitmap = makeCrosshairBitmap(108, dim = false)
                refs.dimBitmap = makeCrosshairBitmap(108, dim = true)
            }

            // User: pulsing green dot
            val u = userLoc
            if (u != null) {
                val ov = refs.user ?: PulsingDotOverlay().also {
                    mapView.overlays.add(it)
                    refs.user = it
                }
                ov.pos = u.osm()
                ov.accuracyM = userAccuracyM
            }

            // Destination crosshairs — sync the marker map with destPoints
            val wantKeys = destPoints.associateBy(::geoKey)
            val activeKey = activeDest?.let(::geoKey)
            val toRemove = refs.dests.keys.toList().filter { it !in wantKeys.keys }
            for (k in toRemove) refs.dests.remove(k)?.let { mapView.overlays.remove(it) }
            for ((k, d) in wantKeys) {
                // Dim only if there IS an active selection AND this isn't it.
                // Pre-selection (active == null) means all four read identical.
                val isDim = activeKey != null && k != activeKey
                val iconBitmap = if (isDim) refs.dimBitmap!! else refs.brightBitmap!!
                val existing = refs.dests[k]
                if (existing == null) {
                    val mk = Marker(mapView).apply {
                        icon = BitmapDrawable(mapView.context.resources, iconBitmap)
                        setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_CENTER)
                        position = d.osm()
                        title = "Walk here"
                        setOnMarkerClickListener { m, _ ->
                            onDestTap(GeoPoint(m.position.latitude, m.position.longitude))
                            true
                        }
                    }
                    mapView.overlays.add(mk)
                    refs.dests[k] = mk
                } else {
                    existing.position = d.osm()
                    existing.icon = BitmapDrawable(mapView.context.resources, iconBitmap)
                }
            }

            // Route polyline — only drawn after the player has picked a dest.
            // Solid when the platform supplied a real walking route; dotted and
            // arrow-capped when it is the bowed guess, which on Android is always.
            // The dotting is the honest signal: a solid line would imply a
            // pavement someone actually looked up.
            val active = activeDest
            if (u != null && active != null) {
                val pts = if (route.isNotEmpty()) route.map { it.osm() } else listOf(u.osm(), active.osm())
                val pl = refs.route ?: Polyline().also {
                    it.outlinePaint.strokeWidth = 9f
                    it.outlinePaint.color = AndroidColor.argb(220, 255, 102, 0)
                    it.outlinePaint.strokeCap = Paint.Cap.ROUND
                    it.outlinePaint.strokeJoin = Paint.Join.ROUND
                    mapView.overlays.add(it)
                    refs.route = it
                }
                // Round caps on a short on/off cycle read as dots rather than
                // dashes; the gap has to clear the cap or they run together.
                pl.outlinePaint.pathEffect =
                    if (routeIsGuess || route.isEmpty()) DashPathEffect(floatArrayOf(2f, 20f), 0f) else null
                pl.setPoints(pts)

                // Arrowhead, drawn as its own solid polyline so the dotting does
                // not eat the barbs.
                if (routeArrow.size >= 2) {
                    val arrow = refs.routeArrow ?: Polyline().also {
                        it.outlinePaint.strokeWidth = 9f
                        it.outlinePaint.color = AndroidColor.argb(220, 255, 102, 0)
                        it.outlinePaint.strokeCap = Paint.Cap.ROUND
                        it.outlinePaint.strokeJoin = Paint.Join.ROUND
                        mapView.overlays.add(it)
                        refs.routeArrow = it
                    }
                    arrow.setPoints(routeArrow.map { it.osm() })
                } else if (refs.routeArrow != null) {
                    mapView.overlays.remove(refs.routeArrow)
                    refs.routeArrow = null
                }
            } else {
                if (refs.route != null) {
                    mapView.overlays.remove(refs.route)
                    refs.route = null
                }
                if (refs.routeArrow != null) {
                    mapView.overlays.remove(refs.routeArrow)
                    refs.routeArrow = null
                }
            }

            // Re-frame when the destination set changes. We use setZoom +
            // setCenter rather than zoomToBoundingBox because the latter
            // depends on the view having been measured — if it runs before
            // first layout (which it often does on the very first round) the
            // computed zoom collapses to ~world view. A fixed close-in zoom
            // centered on the cluster's midpoint is predictable and always
            // shows the player + all four dests on screen at INITIAL_ZOOM.
            if (u != null && destPoints.isNotEmpty() && fitTrigger != refs.lastFit) {
                refs.lastFit = fitTrigger
                val all = listOf(u) + destPoints
                val cLat = all.map { it.latitude }.average()
                val cLon = all.map { it.longitude }.average()
                mapView.controller.setZoom(initialZoom)
                mapView.controller.setCenter(OsmGeoPoint(cLat, cLon))
            }
            mapView.invalidate()
        }
    )
}

private class MapRefs {
    var user: PulsingDotOverlay? = null
    val dests = mutableMapOf<String, Marker>()
    var route: Polyline? = null
    var routeArrow: Polyline? = null
    var lastFit: Int = -1
    var brightBitmap: Bitmap? = null
    var dimBitmap: Bitmap? = null
}

private fun geoKey(p: GeoPoint) = "${p.latitude},${p.longitude}"

// ─────────────────────────────────────────────────────────────────────────────
// CUSTOM OVERLAYS / MARKERS
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Green pulsing dot at [pos], inside a translucent disc the size of the fix's
 * reported accuracy. Self-invalidates the map ~12 fps for the pulse.
 *
 * The disc is the honest part: when the fix is 40 m wide the player can see that
 * it is, instead of watching a confident little dot sit on the wrong side of the
 * street and concluding the game is broken.
 */
private class PulsingDotOverlay : Overlay() {
    var pos: OsmGeoPoint? = null
    var accuracyM: Float = 0f
    private val dotPaint = Paint().apply {
        isAntiAlias = true
        color = AndroidColor.rgb(51, 255, 102)
    }
    private val ringPaint = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 4f
        color = AndroidColor.rgb(51, 255, 102)
    }
    private val accFill = Paint().apply {
        isAntiAlias = true
        color = AndroidColor.argb(38, 51, 255, 102)
    }
    private val accEdge = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 2f
        color = AndroidColor.argb(90, 51, 255, 102)
    }
    private var phase = 0f

    override fun draw(canvas: AndroidCanvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val p = pos ?: return
        val pt = Point()
        mapView.projection.toPixels(p, pt)

        // Accuracy disc: project a point that far due north and measure in pixels,
        // so the circle stays true at any zoom without guessing at a scale factor.
        if (accuracyM > 1f) {
            val edge = Point()
            mapView.projection.toPixels(p.destinationPoint(accuracyM.toDouble(), 0.0), edge)
            val rPx = hypot((edge.x - pt.x).toFloat(), (edge.y - pt.y).toFloat())
            if (rPx > 12f) {
                canvas.drawCircle(pt.x.toFloat(), pt.y.toFloat(), rPx, accFill)
                canvas.drawCircle(pt.x.toFloat(), pt.y.toFloat(), rPx, accEdge)
            }
        }

        phase = (phase + 0.05f) % 1f
        val pulseR = 14f + phase * 40f
        ringPaint.alpha = ((1f - phase) * 200f).toInt().coerceIn(0, 255)
        canvas.drawCircle(pt.x.toFloat(), pt.y.toFloat(), pulseR, ringPaint)
        canvas.drawCircle(pt.x.toFloat(), pt.y.toFloat(), 10f, dotPaint)
        dotPaint.color = AndroidColor.WHITE
        canvas.drawCircle(pt.x.toFloat(), pt.y.toFloat(), 3f, dotPaint)
        dotPaint.color = AndroidColor.rgb(51, 255, 102)
        mapView.postInvalidateDelayed(80)
    }
}

/**
 * Orange crosshair bitmap for the destination marker — square frame + cross.
 * [dim] = true produces a faded version used for un-selected siblings once the
 * player has picked one of the round's options.
 */
private fun makeCrosshairBitmap(size: Int, dim: Boolean = false): Bitmap {
    val bm = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val c = AndroidCanvas(bm)
    val orange = if (dim) AndroidColor.argb(95, 255, 102, 0)
                 else AndroidColor.rgb(255, 102, 0)
    val pStroke = Paint().apply {
        isAntiAlias = true
        color = orange
        style = Paint.Style.STROKE
        strokeWidth = 5f
        strokeCap = Paint.Cap.SQUARE
    }
    val pFill = Paint().apply { isAntiAlias = true; color = orange }
    val cx = size / 2f; val cy = size / 2f
    val r1 = size * 0.42f
    val r2 = size * 0.30f
    val tick = size * 0.10f
    val left = cx - r1; val right = cx + r1; val top = cy - r1; val bot = cy + r1
    c.drawLine(left, top, left + r1 * 0.55f, top, pStroke)
    c.drawLine(right - r1 * 0.55f, top, right, top, pStroke)
    c.drawLine(left, bot, left + r1 * 0.55f, bot, pStroke)
    c.drawLine(right - r1 * 0.55f, bot, right, bot, pStroke)
    c.drawLine(left, top, left, top + r1 * 0.55f, pStroke)
    c.drawLine(left, bot, left, bot - r1 * 0.55f, pStroke)
    c.drawLine(right, top, right, top + r1 * 0.55f, pStroke)
    c.drawLine(right, bot, right, bot - r1 * 0.55f, pStroke)
    c.drawLine(cx, cy - r2, cx, cy - r2 + tick * 2, pStroke)
    c.drawLine(cx, cy + r2, cx, cy + r2 - tick * 2, pStroke)
    c.drawLine(cx - r2, cy, cx - r2 + tick * 2, cy, pStroke)
    c.drawLine(cx + r2, cy, cx + r2 - tick * 2, cy, pStroke)
    c.drawCircle(cx, cy, 5f, pFill)
    return bm
}

