package com.fictioncutshort.justacalculator.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Color as AndroidColor
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Point
import android.graphics.drawable.BitmapDrawable
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Bundle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import org.json.JSONObject
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Overlay
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.TilesOverlay
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.abs
import kotlin.random.Random

// ─────────────────────────────────────────────────────────────────────────────
// BUILDING 5 — Real-world walk (v3, multi-point per round)
//
// Each round the calculator surfaces NEW_PER_ROUND brand-new walkable points
// (plus any still-usable carry-overs from the previous round, capped at
// MAX_OPTIONS), spread out around the player and kept at least MIN_SEPARATION_M
// apart so no two land on top of each other. Collecting any one point counts as
// resolving the round; a fresh selection is then generated from the player's
// current position. Points already collected (plus a small buffer) are excluded
// from future picks so they can't be sent back where they came from.
//
// Door 5 completes once TOTAL_CHECKPOINTS rounds have been resolved — captured,
// or skipped via the help flow (up to MAX_SKIPS, so at least one place is always
// really captured). The gallery then opens over whatever was captured.
//
// Anti-stuck: a "?" help button arms once the player has moved HELP_MOVE_M within
// a round (re-arming each round). It opens a "Having trouble?" flow that can
// capture on the spot, re-roll the destinations, skip the point, or park the walk
// for later. See the help dialogs below.
//
// Stylistically the tiles are inverted to a dark theme to match the calculator
// console; the user is a pulsing green dot, destinations are orange crosshairs.
// At round start all crosshairs read identical; tapping one promotes it to the
// "active" pick — its siblings fade, the OSRM walking route is fetched only
// for it, and "OPEN IN MAPS" appears targeting that exact dest. Arrival
// detection still triggers on ANY dest the player walks into.
// ─────────────────────────────────────────────────────────────────────────────

private const val TOTAL_CHECKPOINTS  = 3
private const val NEW_PER_ROUND      = 3     // brand-new dests generated every round
private const val MAX_OPTIONS        = 5     // new + carried-over options shown at once, capped
private const val MIN_SEPARATION_M   = 45.0  // no two shown dests may sit closer than this
private const val MAX_SKIPS          = 2     // may skip 2 of 3 points and still complete
private const val HELP_MOVE_M        = 20.0  // "?" help button arms after moving this far in a round
private const val TARGET_DIST_MIN_M  = 80.0
private const val TARGET_DIST_MAX_M  = 120.0
private const val OVERPASS_RADIUS_M  = 250
private const val ARRIVAL_RADIUS_M   = 15.0
private const val ARRIVAL_DWELL_MS   = 3_000L
private const val HOME_DEPARTURE_M   = 20.0                 // 3D
private const val VISITED_BUFFER_M   = 30.0                 // exclude future picks within this
private const val GPS_MIN_INTERVAL_MS = 1_000L
private const val GPS_MIN_DIST_M     = 1f
private const val INITIAL_ZOOM       = 18.5    // tight enough that ~100m dests sit well inside the viewport

// Console palette
private val CON_BG       = Color(0xFF0A0F0A)
private val CON_PANEL    = Color(0xE6111111)
private val CON_GREEN    = Color(0xFF33FF66)
private val CON_GREEN_D  = Color(0xFF33AA55)
private val CON_AMBER    = Color(0xFFFFCC44)
private val CON_ORANGE   = Color(0xFFFF6600)

@Composable
fun Building5Map(onComplete: () -> Unit, onExit: () -> Unit) {
    val context = LocalContext.current

    // ── osmdroid one-time init ──────────────────────────────────────────────
    LaunchedEffect(Unit) {
        val cfg = Configuration.getInstance()
        cfg.userAgentValue = context.packageName
        val base = File(context.filesDir, "osmdroid")
        base.mkdirs()
        cfg.osmdroidBasePath = base
        cfg.osmdroidTileCache = File(base, "tiles").also { it.mkdirs() }
    }

    // ── Safety warning gate ─────────────────────────────────────────────────
    var hasStarted by remember { mutableStateOf(false) }

    // ── Permission ──────────────────────────────────────────────────────────
    var hasLocPerm by remember { mutableStateOf(hasLocationPermission(context)) }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted -> hasLocPerm = granted }
    LaunchedEffect(Unit) {
        if (!hasLocPerm) permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    // ── Live location ───────────────────────────────────────────────────────
    var userLoc by remember { mutableStateOf<GeoPoint?>(null) }
    DisposableEffect(hasLocPerm) {
        if (!hasLocPerm) return@DisposableEffect onDispose {}
        val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        val listener = object : LocationListener {
            override fun onLocationChanged(loc: Location) {
                userLoc = GeoPoint(loc.latitude, loc.longitude, loc.altitude)
            }
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
            override fun onProviderEnabled(provider: String) {}
            override fun onProviderDisabled(provider: String) {}
        }
        try {
            (lm.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                ?: lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER))?.let {
                userLoc = GeoPoint(it.latitude, it.longitude, it.altitude)
            }
            if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    GPS_MIN_INTERVAL_MS, GPS_MIN_DIST_M, listener
                )
            }
            if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                lm.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    GPS_MIN_INTERVAL_MS, GPS_MIN_DIST_M, listener
                )
            }
        } catch (_: SecurityException) { /* permission revoked mid-flight */ }
        onDispose { lm.removeUpdates(listener) }
    }

    // ── Home tracking + departure flag ──────────────────────────────────────
    var homeLoc by remember { mutableStateOf<GeoPoint?>(null) }
    var hasDeparted by remember { mutableStateOf(false) }
    LaunchedEffect(userLoc) {
        val u = userLoc ?: return@LaunchedEffect
        if (homeLoc == null) homeLoc = u
        val home = homeLoc ?: return@LaunchedEffect
        // Horizontal-only (Haversine): altitude on phones can drift ±10–20m
        // when stationary, which would otherwise falsely flip hasDeparted.
        if (!hasDeparted && u.distanceToAsDouble(home) > HOME_DEPARTURE_M) hasDeparted = true
    }

    // ── Round state ─────────────────────────────────────────────────────────
    // ── Saved run ───────────────────────────────────────────────────────────
    // This building is a WALK - it happens over real time, in the real world, and
    // it is the single most likely thing in the game to be interrupted. Losing the
    // places already walked to would mean walking them again, so the visited
    // points and the sound mosaics captured at them are written to disk as they
    // happen (see b5Save below), not at the end.
    var checkpointIdx by remember { mutableStateOf(com.fictioncutshort.justacalculator.logic.BuildingProgress.getInt(context, 5, "checkpoint", 0)) }
    var currentDests by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }
    var carryDests by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }
    var visited by remember {
        mutableStateOf(
            com.fictioncutshort.justacalculator.logic.BuildingProgress.getFloats(context, 5, "visited")
                .chunked(2)
                .filter { it.size == 2 }
                .map { GeoPoint(it[0].toDouble(), it[1].toDouble()) }
        )
    }
    LaunchedEffect(visited) {
        com.fictioncutshort.justacalculator.logic.BuildingProgress.putFloats(context, 5, "visited",
            visited.flatMap { listOf(it.latitude.toFloat(), it.longitude.toFloat()) })
    }
    LaunchedEffect(checkpointIdx) { com.fictioncutshort.justacalculator.logic.BuildingProgress.putInt(context, 5, "checkpoint", checkpointIdx) }
    var route by remember { mutableStateOf<List<GeoPoint>>(emptyList()) }
    var activeDest by remember { mutableStateOf<GeoPoint?>(null) }
    var arrivedAt by remember { mutableStateOf<Long?>(null) }
    var checkpointStartLoc by remember { mutableStateOf<GeoPoint?>(null) }
    var completed by remember { mutableStateOf(false) }
    var fitTrigger by remember { mutableStateOf(0) }
    var captureSpot by remember { mutableStateOf<GeoPoint?>(null) }  // arrived spot being scanned
    val captures = remember { mutableStateListOf<PlaceCapture>().apply { addAll(loadCaptures(context)) } }   // one per completed place
    var showGallery by remember { mutableStateOf(false) }

    // Skips used so far (persisted): may skip up to MAX_SKIPS points and still
    // complete the building, so at least one place is always really captured.
    var skipsUsed by remember { mutableStateOf(com.fictioncutshort.justacalculator.logic.BuildingProgress.getInt(context, 5, "skips", 0)) }
    LaunchedEffect(skipsUsed) { com.fictioncutshort.justacalculator.logic.BuildingProgress.putInt(context, 5, "skips", skipsUsed) }

    // "Finish this later" parks the walk: the screen goes blank but for a
    // "continue" button, and the parked flag survives minimise / relaunch so the
    // player picks the walk back up exactly where they left it.
    var parked by remember { mutableStateOf(com.fictioncutshort.justacalculator.logic.BuildingProgress.getInt(context, 5, "parked", 0) == 1) }
    LaunchedEffect(parked) { com.fictioncutshort.justacalculator.logic.BuildingProgress.putInt(context, 5, "parked", if (parked) 1 else 0) }

    // "Having trouble?" help flow. The "?" button arms once the player has moved
    // HELP_MOVE_M within the current round, and re-arms each new round.
    var helpArmed by remember { mutableStateOf(false) }
    var showHelp by remember { mutableStateOf(false) }
    var showHelpReasons by remember { mutableStateOf(false) }
    var showHelpActions by remember { mutableStateOf(false) }
    LaunchedEffect(userLoc, checkpointStartLoc) {
        val u = userLoc; val s = checkpointStartLoc
        if (u != null && s != null && !helpArmed && u.distanceToAsDouble(s) >= HELP_MOVE_M) helpArmed = true
    }

    // ── Generate / refresh the round's destinations ─────────────────────────
    // Triggered when currentDests is empty (start of game OR after resolve).
    // carryDests holds the previous round's unvisited points so they can
    // re-appear in the new selection when Overpass returns few fresh candidates.
    LaunchedEffect(userLoc, currentDests, completed) {
        if (currentDests.isNotEmpty() || completed) return@LaunchedEffect
        val loc = userLoc ?: return@LaunchedEffect
        val carry = carryDests
        // Every round produces NEW_PER_ROUND brand-new places (never re-served
        // carry-overs), spread by bearing AND kept at least MIN_SEPARATION_M apart
        // so two picks never land on top of each other.
        val fresh = withContext(Dispatchers.IO) {
            pickWalkableDestinations(loc, visited, carry, NEW_PER_ROUND)
        }.toMutableList()
        // Overpass can return fewer (sparse streets, small area / first fix), so
        // pad with blind projections spread around the compass — still separated
        // from each other, from visited, and from carry.
        var guard = 0
        while (fresh.size < NEW_PER_ROUND && guard < 24) {
            val bearing = (fresh.size * (360.0 / NEW_PER_ROUND) + Random.nextDouble() * 50.0) % 360.0
            val dist = TARGET_DIST_MIN_M + Random.nextDouble() * (TARGET_DIST_MAX_M - TARGET_DIST_MIN_M)
            val cand = loc.destinationPoint(dist, bearing)
            if (visited.none { it.distanceToAsDouble(cand) < VISITED_BUFFER_M } &&
                fresh.none { it.distanceToAsDouble(cand) < MIN_SEPARATION_M } &&
                carry.none { it.distanceToAsDouble(cand) < MIN_SEPARATION_M }) {
                fresh.add(cand)
            }
            guard++
        }
        // Carried-over unused points stay on offer as EXTRA choices, but only
        // those still in the walking band, clear of visited, and not sitting on
        // top of a fresh pick or another carry-over.
        val keptCarry = mutableListOf<GeoPoint>()
        for (c in carry) {
            val d = loc.distanceToAsDouble(c)
            if (d !in TARGET_DIST_MIN_M..TARGET_DIST_MAX_M) continue
            if (visited.any { it.distanceToAsDouble(c) < VISITED_BUFFER_M }) continue
            if (fresh.any { it.distanceToAsDouble(c) < MIN_SEPARATION_M }) continue
            if (keptCarry.any { it.distanceToAsDouble(c) < MIN_SEPARATION_M }) continue
            keptCarry.add(c)
        }
        currentDests = (fresh + keptCarry).take(MAX_OPTIONS)
        carryDests = emptyList()
        checkpointStartLoc = loc
        helpArmed = false      // re-arm the "?" help button for the new round
        arrivedAt = null
        fitTrigger++
    }

    // activeDest is driven purely by player tap on a crosshair. No automatic
    // nearest-tracking (which would jitter between roughly-equidistant points).
    // resolveRound() clears it back to null when a round ends.

    // ── Fetch walking route to the active dest ──────────────────────────────
    LaunchedEffect(activeDest) {
        val d = activeDest ?: return@LaunchedEffect
        val u = userLoc ?: return@LaunchedEffect
        val pts = withContext(Dispatchers.IO) { fetchWalkingRoute(u, d) }
        route = pts ?: emptyList()
    }

    fun closeHelp() { showHelp = false; showHelpReasons = false; showHelpActions = false }

    // ── Resolve a round (player captured a point OR skipped it) ─────────────
    // Advances the checkpoint. When the final checkpoint resolves the walk is
    // done and the gallery opens over whatever was actually captured.
    fun resolveRound(collected: GeoPoint?) {
        // Anything the player didn't collect this round becomes carry-over for
        // the next pick — keeps the map stable in low-street areas.
        carryDests = if (collected != null) currentDests.filterNot { it === collected }
                     else currentDests
        if (collected != null) visited = visited + collected
        val nextIdx = checkpointIdx + 1
        checkpointIdx = nextIdx
        arrivedAt = null
        closeHelp()
        route = emptyList()
        activeDest = null
        currentDests = emptyList()  // triggers generator LaunchedEffect (unless done)
        if (nextIdx >= TOTAL_CHECKPOINTS) showGallery = true
    }

    // ── Regenerate this round's options without advancing the checkpoint ─────
    // ("give me different places to go" from the help flow.)
    fun regenerate() {
        carryDests = emptyList()
        activeDest = null
        route = emptyList()
        arrivedAt = null
        closeHelp()
        currentDests = emptyList()  // triggers generator LaunchedEffect
    }

    // ── Arrival detection — reaching ANY dest starts a sound scan of the spot ──
    LaunchedEffect(userLoc, currentDests, completed, captureSpot) {
        val u = userLoc
        if (u == null || currentDests.isEmpty() || completed || captureSpot != null) return@LaunchedEffect
        val reached = currentDests.firstOrNull { u.distanceToAsDouble(it) <= ARRIVAL_RADIUS_M }
        if (reached != null) {
            val now = System.currentTimeMillis()
            if (arrivedAt == null) arrivedAt = now
            else if (now - arrivedAt!! >= ARRIVAL_DWELL_MS) captureSpot = reached
        } else {
            arrivedAt = null
        }
    }

    // ── UI ─────────────────────────────────────────────────────────────────
    Box(Modifier.fillMaxSize().background(CON_BG)) {
        // Parked ("finish this later"): everything already saved — show only a
        // way back in. Survives minimise / relaunch via the persisted flag.
        if (parked) {
            ParkedScreen(onContinue = { parked = false; hasStarted = true })
            return@Box
        }
        if (hasStarted) {
            if (!hasLocPerm) {
                FallbackSketchMap(
                    onRequestPerm = { permLauncher.launch(Manifest.permission.ACCESS_FINE_LOCATION) }
                )
            } else {
                BuildingMapView(
                    userLoc = userLoc,
                    destPoints = currentDests,
                    activeDest = activeDest,
                    route = route,
                    fitTrigger = fitTrigger,
                    onDestTap = { tapped -> activeDest = tapped }
                )
            }
        }

        // Narration banner — extra top padding
        val narration = when {
            !hasStarted               -> ""
            !hasLocPerm               -> "I cannot see where you are. May I?"
            userLoc == null           -> "Looking for you..."
            currentDests.isEmpty()    -> "Picking spots near you..."
            completed                 -> "Thank you for indulging me."
            arrivedAt != null         -> "Hold there a moment..."
            !hasDeparted              -> "Move at least 20m from where you started."
            else                      -> {
                val n = (checkpointIdx + 1).coerceAtMost(TOTAL_CHECKPOINTS)
                "Tap one. Walk there. ($n/$TOTAL_CHECKPOINTS)"
            }
        }
        if (hasStarted && narration.isNotEmpty()) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 56.dp, start = 16.dp, end = 16.dp)
                    .background(CON_PANEL, shape = RoundedCornerShape(4.dp))
                    .border(1.dp, CON_GREEN_D, shape = RoundedCornerShape(4.dp))
                    .padding(horizontal = 16.dp, vertical = 10.dp)
            ) {
                Text(
                    narration,
                    color = CON_AMBER,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center
                )
            }
        }

        // "?" help button — arms once the player has actually set off (moved
        // HELP_MOVE_M this round) and re-arms each new round. Replaces the old
        // fixed-timer "stuck?" prompt, which fired at the wrong times.
        if (hasStarted && hasLocPerm && !completed && captureSpot == null &&
            !showGallery && helpArmed && currentDests.isNotEmpty()) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 52.dp, end = 16.dp)
                    .size(40.dp)
                    .background(CON_PANEL, shape = RoundedCornerShape(20.dp))
                    .border(1.dp, CON_GREEN_D, shape = RoundedCornerShape(20.dp))
                    .clickable { showHelp = true },
                contentAlignment = Alignment.Center
            ) {
                Text("?", color = CON_AMBER, fontSize = 20.sp,
                     fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
        }

        // "Open in Maps" only shows once the player has tapped a specific
        // crosshair — it hands routing off to their preferred map app, targeted
        // at exactly the dest they picked.
        val picked = activeDest
        if (hasStarted && hasLocPerm && picked != null && !completed) {
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 32.dp)
                    .background(CON_ORANGE, shape = RoundedCornerShape(3.dp))
                    .border(1.dp, CON_GREEN_D, shape = RoundedCornerShape(3.dp))
                    .clickable {
                        val uri = Uri.parse(
                            "geo:${picked.latitude},${picked.longitude}" +
                            "?q=${picked.latitude},${picked.longitude}(Walk%20here)"
                        )
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, uri).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                }
                            )
                        }
                    }
                    .padding(horizontal = 28.dp, vertical = 12.dp)
            ) {
                Text("[ OPEN IN MAPS ]",
                     color = Color.White, fontSize = 14.sp,
                     fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
        }

        if (!hasStarted) {
            SafetyWarningDialog(onProceed = { hasStarted = true })
        }

        // ── "Having trouble?" help flow (opened by the "?" button) ──────────
        if (showHelp && !completed && captureSpot == null) {
            HelpTroubleDialog(
                onNo  = { closeHelp() },
                onYes = { showHelp = false; showHelpReasons = true }
            )
        }
        if (showHelpReasons && !completed && captureSpot == null) {
            HelpReasonDialog(
                onReason = { showHelpReasons = false; showHelpActions = true }
            )
        }
        if (showHelpActions && !completed && captureSpot == null) {
            HelpActionDialog(
                canSkip = skipsUsed < MAX_SKIPS,
                onListenHere = {
                    // Capture right where they are, counting toward completion.
                    closeHelp()
                    captureSpot = userLoc ?: activeDest ?: currentDests.firstOrNull()
                },
                onDifferentPlaces = { regenerate() },
                onSkip = {
                    if (skipsUsed < MAX_SKIPS) {
                        skipsUsed++
                        resolveRound(null)   // advance past this point without a capture
                    }
                },
                onFinishLater = {
                    // Everything (visited / checkpoint / captures / skips) is
                    // already persisted; just park and back out to the button.
                    closeHelp()
                    parked = true
                }
            )
        }

        // Arrived at a spot → full-screen sound scan. Each finished scan is kept
        // (with its coords / time / dominant freq). Resolving the round advances
        // the checkpoint and, on the last one, opens the gallery.
        captureSpot?.let { spot ->
            val lastStop = checkpointIdx + 1 >= TOTAL_CHECKPOINTS
            Building5SoundProto(
                buttonLabel = if (lastStop) "Finish" else "Let's find the next stop",
                onComplete = { mosaic ->
                    captures.add(
                        PlaceCapture(
                            index = captures.size + 1,
                            mosaic = mosaic,
                            lat = spot.latitude,
                            lon = spot.longitude,
                            timeMs = System.currentTimeMillis(),
                            dominantHz = dominantHz(mosaic)
                        )
                    )
                    saveCaptures(context, captures.toList())
                    captureSpot = null
                    resolveRound(spot)   // opens the gallery itself when it's the last stop
                }
            )
        }

        // Final gallery — all three places, save, then back to the city.
        if (showGallery) {
            SoundMosaicGallery(
                captures = captures,
                onBack = {
                    completed = true
                    // 5 cookies per location captured.
                    com.fictioncutshort.justacalculator.logic.CurrencyStore.award(
                        context, com.fictioncutshort.justacalculator.logic.Currency.COOKIES,
                        captures.size * 5, "b5")
                    onComplete()
                }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MAP VIEW
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BuildingMapView(
    userLoc: GeoPoint?,
    destPoints: List<GeoPoint>,
    activeDest: GeoPoint?,
    route: List<GeoPoint>,
    fitTrigger: Int,
    onDestTap: (GeoPoint) -> Unit
) {
    val refs = remember { MapRefs() }

    AndroidView(
        factory = { ctx ->
            MapView(ctx).apply {
                setTileSource(TileSourceFactory.MAPNIK)
                overlayManager.tilesOverlay.setColorFilter(TilesOverlay.INVERT_COLORS)
                setMultiTouchControls(true)
                isHorizontalMapRepetitionEnabled = false
                isVerticalMapRepetitionEnabled = false
                controller.setZoom(INITIAL_ZOOM)
                userLoc?.let { controller.setCenter(it) }
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
                ov.pos = u
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
                        position = d
                        title = "Walk here"
                        setOnMarkerClickListener { m, _ ->
                            onDestTap(m.position)
                            true
                        }
                    }
                    mapView.overlays.add(mk)
                    refs.dests[k] = mk
                } else {
                    existing.position = d
                    existing.icon = BitmapDrawable(mapView.context.resources, iconBitmap)
                }
            }

            // Route polyline — only drawn after the player has picked a dest.
            // Solid orange when OSRM returned a route, dashed straight line
            // while the route is in flight or after a fetch failure.
            val active = activeDest
            if (u != null && active != null) {
                val pts = if (route.isNotEmpty()) route else listOf(u, active)
                val pl = refs.route ?: Polyline().also {
                    it.outlinePaint.strokeWidth = 9f
                    it.outlinePaint.color = AndroidColor.argb(220, 255, 102, 0)
                    it.outlinePaint.strokeCap = Paint.Cap.ROUND
                    it.outlinePaint.strokeJoin = Paint.Join.ROUND
                    mapView.overlays.add(it)
                    refs.route = it
                }
                pl.outlinePaint.pathEffect =
                    if (route.isEmpty()) DashPathEffect(floatArrayOf(22f, 14f), 0f) else null
                pl.setPoints(pts)
            } else if (refs.route != null) {
                mapView.overlays.remove(refs.route)
                refs.route = null
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
                mapView.controller.setZoom(INITIAL_ZOOM)
                mapView.controller.setCenter(GeoPoint(cLat, cLon))
            }
            mapView.invalidate()
        }
    )
}

private class MapRefs {
    var user: PulsingDotOverlay? = null
    val dests = mutableMapOf<String, Marker>()
    var route: Polyline? = null
    var lastFit: Int = -1
    var brightBitmap: Bitmap? = null
    var dimBitmap: Bitmap? = null
}

private fun geoKey(p: GeoPoint) = "${p.latitude},${p.longitude}"

// ─────────────────────────────────────────────────────────────────────────────
// CUSTOM OVERLAYS / MARKERS
// ─────────────────────────────────────────────────────────────────────────────

/** Green pulsing dot at [pos]. Self-invalidates the map ~12 fps for the pulse. */
private class PulsingDotOverlay : Overlay() {
    var pos: GeoPoint? = null
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
    private var phase = 0f

    override fun draw(canvas: AndroidCanvas, mapView: MapView, shadow: Boolean) {
        if (shadow) return
        val p = pos ?: return
        val pt = Point()
        mapView.projection.toPixels(p, pt)
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

// ─────────────────────────────────────────────────────────────────────────────
// DIALOGS
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SafetyWarningDialog(onProceed: () -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.85f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {},
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .background(CON_PANEL, shape = RoundedCornerShape(4.dp))
                .border(1.dp, CON_GREEN_D, shape = RoundedCornerShape(4.dp))
                .padding(horizontal = 28.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(">>> CAUTION <<<",
                 color = CON_AMBER, fontSize = 16.sp,
                 fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(14.dp))
            Text(
                "You're about to go OUTSIDE.\n\n" +
                "• Watch for traffic.\n" +
                "• Don't trespass.\n" +
                "• If a destination looks unsafe — skip it.\n" +
                "• Check your battery — consider a power bank.\n\n" +
                "Trust your eyes. I only know what the map tells me.",
                color = CON_GREEN, fontSize = 13.sp,
                fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(20.dp))
            Box(
                Modifier
                    .background(CON_ORANGE, shape = RoundedCornerShape(3.dp))
                    .clickable { onProceed() }
                    .padding(horizontal = 28.dp, vertical = 12.dp)
            ) {
                Text("[ I UNDERSTAND ]",
                     color = Color.White, fontSize = 14.sp,
                     fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ── Help flow ("?" button) ───────────────────────────────────────────────────
// Three stacked dialogs: "Having trouble?" → pick a reason → pick an action.

/** Modal scaffold shared by the help dialogs: dimmed backdrop + console panel. */
@Composable
private fun ConsoleModal(content: @Composable ColumnScope.() -> Unit) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.80f))
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() }
            ) {},
        contentAlignment = Alignment.Center
    ) {
        Column(
            Modifier
                .padding(horizontal = 24.dp)
                .background(CON_PANEL, shape = RoundedCornerShape(4.dp))
                .border(1.dp, CON_GREEN_D, shape = RoundedCornerShape(4.dp))
                .padding(horizontal = 28.dp, vertical = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
    }
}

/** Console-styled button. [primary] = orange call-to-action; disabled = greyed. */
@Composable
private fun ConsoleButton(
    label: String,
    primary: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val bg = when { !enabled -> Color(0xFF1A1A1A); primary -> CON_ORANGE; else -> Color(0xFF2A2A2A) }
    val fg = when { !enabled -> Color(0xFF555555); primary -> Color.White; else -> CON_GREEN }
    Box(
        modifier
            .background(bg, shape = RoundedCornerShape(3.dp))
            .border(1.dp, if (enabled) CON_GREEN_D else Color(0xFF333333), shape = RoundedCornerShape(3.dp))
            .then(if (enabled) Modifier.clickable { onClick() } else Modifier)
            .padding(horizontal = 20.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, color = fg, fontSize = 13.sp,
             fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold,
             textAlign = TextAlign.Center)
    }
}

@Composable
private fun HelpTroubleDialog(onNo: () -> Unit, onYes: () -> Unit) {
    ConsoleModal {
        Text("Having trouble?",
             color = CON_AMBER, fontSize = 15.sp, fontWeight = FontWeight.Bold,
             fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(18.dp))
        ConsoleButton("No, just testing", modifier = Modifier.fillMaxWidth(), onClick = onNo)
        Spacer(Modifier.height(10.dp))
        ConsoleButton("Yes", primary = true, modifier = Modifier.fillMaxWidth(), onClick = onYes)
    }
}

@Composable
private fun HelpReasonDialog(onReason: (String) -> Unit) {
    val reasons = listOf(
        "Path is blocked", "It doesn't seem safe",
        "Private property", "I'm tired", "The weather"
    )
    ConsoleModal {
        Text("What's up?",
             color = CON_AMBER, fontSize = 15.sp, fontWeight = FontWeight.Bold,
             fontFamily = FontFamily.Monospace)
        Spacer(Modifier.height(18.dp))
        reasons.forEachIndexed { i, r ->
            if (i > 0) Spacer(Modifier.height(9.dp))
            ConsoleButton(r, modifier = Modifier.fillMaxWidth()) { onReason(r) }
        }
    }
}

@Composable
private fun HelpActionDialog(
    canSkip: Boolean,
    onListenHere: () -> Unit,
    onDifferentPlaces: () -> Unit,
    onSkip: () -> Unit,
    onFinishLater: () -> Unit
) {
    ConsoleModal {
        Text("Fair. What would you like to do now?",
             color = CON_AMBER, fontSize = 14.sp, fontWeight = FontWeight.Bold,
             fontFamily = FontFamily.Monospace, textAlign = TextAlign.Center)
        Spacer(Modifier.height(18.dp))
        ConsoleButton("Help me complete this point", primary = true,
                      modifier = Modifier.fillMaxWidth(), onClick = onListenHere)
        Spacer(Modifier.height(10.dp))
        ConsoleButton("Give me different places to go",
                      modifier = Modifier.fillMaxWidth(), onClick = onDifferentPlaces)
        Spacer(Modifier.height(10.dp))
        ConsoleButton(if (canSkip) "Skip this point" else "Skip this point (no skips left)",
                      enabled = canSkip, modifier = Modifier.fillMaxWidth(), onClick = onSkip)
        Spacer(Modifier.height(10.dp))
        ConsoleButton("Finish this later",
                      modifier = Modifier.fillMaxWidth(), onClick = onFinishLater)
    }
}

/** Parked screen — the whole walk collapses to one "continue" button. */
@Composable
private fun ParkedScreen(onContinue: () -> Unit) {
    Box(Modifier.fillMaxSize().background(CON_BG), contentAlignment = Alignment.Center) {
        ConsoleButton("continue with building 5", primary = true, onClick = onContinue)
    }
}

@Composable
private fun FallbackSketchMap(onRequestPerm: () -> Unit) {
    Box(Modifier.fillMaxSize().background(CON_BG)) {
        Canvas(Modifier.fillMaxSize()) {
            val cx = size.width / 2f; val cy = size.height / 2f
            val gridCol = Color(0xFF223322)
            val step = 56f
            var x = 0f
            while (x < size.width) {
                drawLine(gridCol, Offset(x, 0f), Offset(x, size.height), 1f); x += step
            }
            var y = 0f
            while (y < size.height) {
                drawLine(gridCol, Offset(0f, y), Offset(size.width, y), 1f); y += step
            }
            drawCircle(CON_GREEN, 14f, Offset(cx, cy))
            drawCircle(CON_GREEN.copy(alpha = 0.40f), 40f, Offset(cx, cy), style = Stroke(2f))
            drawCircle(CON_GREEN.copy(alpha = 0.18f), 90f, Offset(cx, cy), style = Stroke(2f))
        }
        Column(
            Modifier.align(Alignment.Center).padding(top = 140.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text("YOU ARE HERE",
                 color = CON_GREEN, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
            Spacer(Modifier.height(40.dp))
            Box(
                Modifier
                    .background(CON_ORANGE, shape = RoundedCornerShape(3.dp))
                    .clickable { onRequestPerm() }
                    .padding(horizontal = 22.dp, vertical = 10.dp)
            ) {
                Text("[ ALLOW LOCATION ]",
                     color = Color.White, fontSize = 13.sp,
                     fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// HELPERS
// ─────────────────────────────────────────────────────────────────────────────

private fun hasLocationPermission(context: Context): Boolean =
    ContextCompat.checkSelfPermission(
        context, Manifest.permission.ACCESS_FINE_LOCATION
    ) == PackageManager.PERMISSION_GRANTED

/**
 * Bearing in degrees [0,360) from [from] to [to]. Used by the spread picker
 * to space chosen dests evenly around the player.
 */
private fun bearingDeg(from: GeoPoint, to: GeoPoint): Double {
    val phi1 = Math.toRadians(from.latitude)
    val phi2 = Math.toRadians(to.latitude)
    val dLambda = Math.toRadians(to.longitude - from.longitude)
    val y = kotlin.math.sin(dLambda) * kotlin.math.cos(phi2)
    val x = kotlin.math.cos(phi1) * kotlin.math.sin(phi2) -
            kotlin.math.sin(phi1) * kotlin.math.cos(phi2) * kotlin.math.cos(dLambda)
    return (Math.toDegrees(kotlin.math.atan2(y, x)) + 360.0) % 360.0
}

/**
 * Walking-route polyline from the public OSRM demo (foot profile). NOTE:
 * router.project-osrm.org is rate-limited and "not for production" — swap the
 * URL for a self-hosted OSRM or paid GraphHopper before shipping.
 */
private fun fetchWalkingRoute(from: GeoPoint, to: GeoPoint): List<GeoPoint>? {
    return try {
        val url = URL(
            "https://router.project-osrm.org/route/v1/foot/" +
            "${from.longitude},${from.latitude};${to.longitude},${to.latitude}" +
            "?overview=full&geometries=geojson"
        )
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 15_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "JustACalculator/1.x (osmdroid)")
        }
        val body = conn.inputStream.use {
            BufferedReader(InputStreamReader(it)).readText()
        }
        val routes = JSONObject(body).optJSONArray("routes") ?: return null
        if (routes.length() == 0) return null
        val coords = routes.getJSONObject(0)
            .getJSONObject("geometry").getJSONArray("coordinates")
        val pts = ArrayList<GeoPoint>(coords.length())
        for (i in 0 until coords.length()) {
            val c = coords.getJSONArray(i)
            pts.add(GeoPoint(c.getDouble(1), c.getDouble(0)))  // GeoJSON = [lon, lat]
        }
        pts
    } catch (_: Exception) {
        null
    }
}

/**
 * Pick up to [n] brand-new OSM road-vertices 80–120 m from [user], spread out by
 * bearing and never closer than MIN_SEPARATION_M to each other. Pedestrian-
 * friendly highway types are preferred; residential / unclassified / service
 * used as fallback (covers US-style suburbs without footway tagging). Candidates
 * within VISITED_BUFFER_M of any [visited], or within MIN_SEPARATION_M of any
 * [carryOver] the caller will re-offer, are excluded so picks are genuinely new.
 * May return fewer than [n]; the caller tops up with blind projections.
 */
private fun pickWalkableDestinations(
    user: GeoPoint,
    visited: List<GeoPoint>,
    carryOver: List<GeoPoint>,
    n: Int
): List<GeoPoint> {
    val pedTypes = setOf("footway", "pedestrian", "path", "cycleway", "living_street")
    val privateAccess = setOf("private", "no", "customers", "delivery")
    val noFoot = setOf("no", "private")
    val skipService = setOf("driveway", "parking_aisle")
    val pedestrian = mutableListOf<GeoPoint>()
    val road = mutableListOf<GeoPoint>()

    try {
        val query = """
            [out:json][timeout:20];
            way(around:$OVERPASS_RADIUS_M,${user.latitude},${user.longitude})
                ["highway"~"^(footway|pedestrian|path|cycleway|living_street|residential|tertiary|unclassified|service)$"];
            out tags geom;
        """.trimIndent()
        val url = URL(
            "https://overpass-api.de/api/interpreter?data=" +
            URLEncoder.encode(query, "UTF-8")
        )
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 15_000
            requestMethod = "GET"
            setRequestProperty("User-Agent", "JustACalculator/1.x (osmdroid)")
        }
        val body = conn.inputStream.use {
            BufferedReader(InputStreamReader(it)).readText()
        }
        val elements = JSONObject(body).optJSONArray("elements") ?: return emptyList()
        for (i in 0 until elements.length()) {
            val el = elements.optJSONObject(i) ?: continue
            val tags = el.optJSONObject("tags") ?: continue
            if (tags.optString("access") in privateAccess) continue
            if (tags.optString("foot") in noFoot) continue
            val highway = tags.optString("highway")
            if (highway == "service" && tags.optString("service") in skipService) continue
            val target = if (highway in pedTypes) pedestrian else road
            val geom = el.optJSONArray("geometry") ?: continue
            for (j in 0 until geom.length()) {
                val pt = geom.optJSONObject(j) ?: continue
                val p = GeoPoint(pt.optDouble("lat"), pt.optDouble("lon"))
                val d = user.distanceToAsDouble(p)
                if (d in TARGET_DIST_MIN_M..TARGET_DIST_MAX_M) target.add(p)
            }
        }
    } catch (_: Exception) {
        // Network/parse failure → just use whatever we can carry over
    }

    fun nearVisited(p: GeoPoint) =
        visited.any { v -> v.distanceToAsDouble(p) < VISITED_BUFFER_M }
    // Fresh picks must also stay clear of the carry-overs the caller re-offers,
    // so a round never shows the "same" place as both a new and a carried point.
    fun nearCarry(p: GeoPoint) =
        carryOver.any { c -> c.distanceToAsDouble(p) < MIN_SEPARATION_M }

    val pedPool = pedestrian.distinctBy(::geoKey).filterNot { nearVisited(it) || nearCarry(it) }
    val roadPool = road.distinctBy(::geoKey).filterNot { nearVisited(it) || nearCarry(it) }

    // Prefer pedestrian-friendly ways; top up with roads if there aren't enough.
    val pool = if (pedPool.size >= n) pedPool else (pedPool + roadPool).distinctBy(::geoKey)
    if (pool.isEmpty()) return emptyList()
    return spreadDistinct(pool, user, n, MIN_SEPARATION_M)
}

/**
 * Farthest-first sampling from [user]: starts with a random pick, then
 * iteratively adds the candidate whose minimum angular separation from the
 * already-picked points is greatest — but never one closer than [minSepM] to a
 * pick, so the result is both spread across the compass AND never two markers on
 * top of each other. May return fewer than [n] if nothing is far enough apart;
 * the caller pads with blind projections.
 */
private fun spreadDistinct(pool: List<GeoPoint>, user: GeoPoint, n: Int, minSepM: Double): List<GeoPoint> {
    if (pool.isEmpty()) return emptyList()
    val picked = mutableListOf(pool[Random.nextInt(pool.size)])
    while (picked.size < n) {
        var best: GeoPoint? = null
        var bestScore = -1.0
        for (c in pool) {
            if (picked.any { it.distanceToAsDouble(c) < minSepM }) continue
            val score = picked.minOf { p -> angularSep(user, c, p) }
            if (score > bestScore) { bestScore = score; best = c }
        }
        best?.let { picked.add(it) } ?: break
    }
    return picked
}

private fun angularSep(user: GeoPoint, a: GeoPoint, b: GeoPoint): Double {
    var diff = abs(bearingDeg(user, a) - bearingDeg(user, b))
    if (diff > 180.0) diff = 360.0 - diff
    return diff
}


// ── Saved captures ───────────────────────────────────────────────────────────
// A mosaic is only numbers - a grid of (timeSec, freqHz, loud) cells - so a
// capture flattens to text without losing anything the gallery draws. Stored as
// one record per place: index|lat|lon|timeMs|dominantHz|label|rows|cols|cells...
// No raw audio is kept, here or anywhere else (see Building5SoundProto).

private fun saveCaptures(context: android.content.Context, list: List<PlaceCapture>) {
    val sb = StringBuilder()
    for (c in list) {
        val cells = c.mosaic.cells
        val rows = cells.size
        val cols = if (rows > 0) cells[0].size else 0
        sb.append(c.index).append('|')
            .append(c.lat).append('|').append(c.lon).append('|')
            .append(c.timeMs).append('|').append(c.dominantHz).append('|')
            .append(c.mosaic.label.replace('|', ' ').replace(';', ' ')).append('|')
            .append(rows).append('|').append(cols).append('|')
        for (r in 0 until rows) for (cc in 0 until cols) {
            val cell = cells[r][cc]
            sb.append(cell.timeSec).append(',').append(cell.freqHz).append(',').append(cell.loud).append(' ')
        }
        sb.append(';')
    }
    com.fictioncutshort.justacalculator.logic.BuildingProgress.putString(context, 5, "captures", sb.toString())
}

private fun loadCaptures(context: android.content.Context): List<PlaceCapture> {
    val raw = com.fictioncutshort.justacalculator.logic.BuildingProgress.getString(context, 5, "captures")
    if (raw.isBlank()) return emptyList()
    val out = mutableListOf<PlaceCapture>()
    for (rec in raw.split(';')) {
        if (rec.isBlank()) continue
        val f = rec.split('|')
        if (f.size < 9) continue
        try {
            val rows = f[6].toInt()
            val cols = f[7].toInt()
            val flat = f[8].trim().split(' ').filter { it.isNotBlank() }
            if (rows <= 0 || cols <= 0 || flat.size < rows * cols) continue
            val cells = Array(rows) { r ->
                Array(cols) { c ->
                    val p = flat[r * cols + c].split(',')
                    MosaicCell(p[0].toFloat(), p[1].toFloat(), p[2].toFloat())
                }
            }
            out.add(
                PlaceCapture(
                    index = f[0].toInt(),
                    mosaic = SoundMosaic(cells, f[5], "", emptyList()),
                    lat = f[1].toDouble(),
                    lon = f[2].toDouble(),
                    timeMs = f[3].toLong(),
                    dominantHz = f[4].toInt(),
                )
            )
        } catch (_: Throwable) { /* a corrupt record is dropped, not fatal */ }
    }
    return out
}
