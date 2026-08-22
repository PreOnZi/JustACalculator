package com.fictioncutshort.justacalculator.ui.screens

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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import com.fictioncutshort.justacalculator.gl.JsonObj
import com.fictioncutshort.justacalculator.platform.nowMillis
import com.fictioncutshort.justacalculator.platform.AppContext
import com.fictioncutshort.justacalculator.platform.AppPermission
import com.fictioncutshort.justacalculator.platform.GeoPoint
import com.fictioncutshort.justacalculator.platform.platformWalkingRoute
import com.fictioncutshort.justacalculator.platform.LocationFix
import com.fictioncutshort.justacalculator.platform.currentAppContext
import com.fictioncutshort.justacalculator.platform.hasPermission
import com.fictioncutshort.justacalculator.platform.httpGetText
import com.fictioncutshort.justacalculator.platform.openMapsAt
import com.fictioncutshort.justacalculator.platform.rememberPermissionRequest
import com.fictioncutshort.justacalculator.platform.rememberPermissionState
import com.fictioncutshort.justacalculator.platform.startLocationUpdates
import com.fictioncutshort.justacalculator.platform.urlEncode
import com.fictioncutshort.justacalculator.platform.destinationPoint
import com.fictioncutshort.justacalculator.platform.distanceToAsDouble
import com.fictioncutshort.justacalculator.platform.PlatformBuildingMapView
import kotlin.math.abs
import kotlin.math.hypot
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
// "active" pick — its siblings fade, a route to it is drawn (a real walking
// route on iOS, a bowed dashed guess elsewhere), and "OPEN IN MAPS" appears
// targeting that exact dest. Arrival
// detection still triggers on ANY dest the player walks into.
// ─────────────────────────────────────────────────────────────────────────────

private const val TOTAL_CHECKPOINTS  = 3
private const val NEW_PER_ROUND      = 3     // brand-new dests generated every round
private const val ROAD_CANDIDATES    = 8     // on-road options fetched per round to choose/pad from
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
private const val LOC_MAX_ACCURACY_M = 75f      // worse than this = a tower guess, not a fix
private const val LOC_STALE_MS       = 25_000L  // held fix older than this → prefer a fresh good one
private const val LOC_ANCIENT_MS     = 60_000L  // …older than this → take whatever arrives
private const val POOR_ACCURACY_M    = 30f      // above this the map says so rather than pretending
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
    val context = currentAppContext()

    // ── Safety warning gate ─────────────────────────────────────────────────
    var hasStarted by remember { mutableStateOf(false) }

    // ── Permission ──────────────────────────────────────────────────────────
    // Re-read on resume — a one-time grant does not survive backgrounding, and
    // the walk beat sends the player out of the app by design.
    val locGranted = rememberPermissionState(AppPermission.LOCATION)
    var hasLocPerm by locGranted
    val requestLocPerm = rememberPermissionRequest(AppPermission.LOCATION) { granted ->
        hasLocPerm = granted
    }
    LaunchedEffect(hasLocPerm) { if (!hasLocPerm) requestLocPerm() }

    // ── Live location ───────────────────────────────────────────────────────
    // Every fix from both providers used to be taken at face value. NETWORK fixes
    // are derived from cell towers and wifi and are routinely hundreds of metres
    // out; one arriving between two good GPS fixes threw the dot (and, on a
    // re-fit, the whole map) somewhere the player wasn't — which is what "50+
    // metres off" and "the dot disappeared" both are. Fixes are now filtered
    // through isBetterLocation, and the accuracy travels with them so the map can
    // DRAW the uncertainty rather than quietly assert a precision it doesn't have.
    var userLoc by remember { mutableStateOf<GeoPoint?>(null) }
    var userAccuracyM by remember { mutableStateOf(0f) }
    DisposableEffect(hasLocPerm) {
        if (!hasLocPerm) return@DisposableEffect onDispose {}
        var bestFix: LocationFix? = null
        val updates = startLocationUpdates(GPS_MIN_INTERVAL_MS, GPS_MIN_DIST_M) { fix ->
            if (!isBetterLocation(fix, bestFix)) return@startLocationUpdates
            bestFix = fix
            userLoc = fix.toGeoPoint()
            userAccuracyM = fix.accuracyM
        }
        onDispose { updates?.stop() }
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
    // True while `route` is the bowed dashed guess rather than a real walking
    // route, so the map knows to dash it and cap it with an arrow.
    var routeIsGuess by remember { mutableStateOf(false) }
    // True when this round's points are blind compass projections rather than real
    // OSM road vertices — nothing checked what's under them, so say so.
    var blindRound by remember { mutableStateOf(false) }
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
        // As many real ON-ROAD candidates as the area offers, spread around us.
        val roadPts = withContext(Dispatchers.Default) {
            pickWalkableDestinations(loc, visited, ROAD_CANDIDATES)
        }
        fun nearCarry(p: GeoPoint) = carry.any { it.distanceToAsDouble(p) < MIN_SEPARATION_M }
        // Prefer brand-new road points (ones that aren't a carry-over) for the round.
        val fresh = mutableListOf<GeoPoint>()
        for (p in roadPts) {
            if (fresh.size >= NEW_PER_ROUND) break
            if (nearCarry(p)) continue
            if (fresh.any { it.distanceToAsDouble(p) < MIN_SEPARATION_M }) continue
            fresh.add(p)
        }
        // Short on fresh ones? Top up from the remaining ROAD points (even near a
        // carry-over) BEFORE ever inventing an off-road target.
        if (fresh.size < NEW_PER_ROUND) for (p in roadPts) {
            if (fresh.size >= NEW_PER_ROUND) break
            if (fresh.any { it.distanceToAsDouble(p) < MIN_SEPARATION_M }) continue
            fresh.add(p)
        }
        // Absolute last resort — Overpass gave us nothing at all (every mirror down,
        // or genuinely no mapped street within OVERPASS_RADIUS_M). These are blind
        // compass projections: nothing knows what's under them, so they can land in
        // a field, and the player is told as much rather than being sent to a point
        // the game is pretending it chose. The base bearing is random per round —
        // it used to start at due north every time, which is why a re-roll handed
        // back the same three directions.
        blindRound = roadPts.isEmpty()
        if (roadPts.isEmpty()) {
            val baseBearing = Random.nextDouble() * 360.0
            var guard = 0
            while (fresh.size < NEW_PER_ROUND && guard < 24) {
                val bearing = (baseBearing + fresh.size * (360.0 / NEW_PER_ROUND) +
                    Random.nextDouble() * 50.0) % 360.0
                val dist = TARGET_DIST_MIN_M + Random.nextDouble() * (TARGET_DIST_MAX_M - TARGET_DIST_MIN_M)
                val cand = loc.destinationPoint(dist, bearing)
                if (visited.none { it.distanceToAsDouble(cand) < VISITED_BUFFER_M } &&
                    fresh.none { it.distanceToAsDouble(cand) < MIN_SEPARATION_M }) fresh.add(cand)
                guard++
            }
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

    // ── Route to the active dest ────────────────────────────────────────────
    // iOS gets a real walking route from MapKit; Android has no free equivalent
    // and returns null, as does iOS when MapKit cannot route. Either way the
    // player sees a line — the bowed dashed guess stands in, so the chapter looks
    // the same on both platforms and works with no network at all.
    LaunchedEffect(activeDest) {
        val d = activeDest ?: return@LaunchedEffect
        val u = userLoc ?: return@LaunchedEffect
        // Draw the guess immediately rather than leaving the map bare while the
        // request is in flight; a real route replaces it if one arrives.
        route = guessPath(u, d)
        routeIsGuess = true
        val real = platformWalkingRoute(u, d)
        if (real != null && real.size >= 2) {
            route = real
            routeIsGuess = false
        }
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
            val now = nowMillis()
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
                    onRequestPerm = { requestLocPerm() }
                )
            } else {
                PlatformBuildingMapView(
                    userLoc = userLoc,
                    userAccuracyM = userAccuracyM,
                    destPoints = currentDests,
                    activeDest = activeDest,
                    route = route,
                    routeIsGuess = routeIsGuess,
                    routeArrow = if (routeIsGuess) arrowHead(route) else emptyList(),
                    fitTrigger = fitTrigger,
                    initialZoom = INITIAL_ZOOM,
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
            // A fix this wide can put the dot a street away. Say so — the shaded
            // circle around the dot is how far out it might be.
            userAccuracyM > POOR_ACCURACY_M ->
                "I can only see you to about ${userAccuracyM.toInt()} m. Give it a moment outdoors."
            // No street data came back, so these are guesses on a bearing. Don't
            // let the player walk into a field thinking the game meant it.
            blindRound ->
                "I couldn't read the streets here — these are rough directions. Use \"?\" for others."
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
                        openMapsAt(context, picked.latitude, picked.longitude, "Walk here")
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
                            timeMs = nowMillis(),
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

/**
 * Is [candidate] worth switching to, given the fix we're already holding?
 *
 * The classic Android heuristic, trimmed to what a walking game needs: a fix far
 * newer than ours wins (the player has moved and our fix is history), an older
 * one never does, and between two roughly-contemporary fixes accuracy decides.
 * A fix worse than [LOC_MAX_ACCURACY_M] is only ever accepted when we have
 * nothing better — that's the tower-triangulated one that used to teleport the
 * dot across the neighbourhood.
 */
/** Identity for a point, so duplicate candidates collapse. */
private fun geoKey(p: GeoPoint) = "${p.latitude},${p.longitude}"

private fun isBetterLocation(candidate: LocationFix, current: LocationFix?): Boolean {
    if (current == null) return true
    val accCand = if (candidate.accuracyM > 0f) candidate.accuracyM else Float.MAX_VALUE
    val accCurr = if (current.accuracyM > 0f) current.accuracyM else Float.MAX_VALUE
    val dt = candidate.timeMs - current.timeMs
    if (dt > LOC_ANCIENT_MS) return true                          // ours is ancient
    if (dt > LOC_STALE_MS && accCand <= LOC_MAX_ACCURACY_M) return true
    if (dt < 0) return false                                      // older than ours
    if (accCand > LOC_MAX_ACCURACY_M && accCurr <= LOC_MAX_ACCURACY_M) return false
    if (accCand <= accCurr) return true
    // A bit worse, but appreciably newer — still better than standing still.
    return dt > 10_000L && accCand <= accCurr * 1.5f
}


/**
 * Bearing in degrees [0,360) from [from] to [to]. Used by the spread picker
 * to space chosen dests evenly around the player.
 */
private const val DEG_TO_RAD = kotlin.math.PI / 180.0

private fun bearingDeg(from: GeoPoint, to: GeoPoint): Double {
    val phi1 = from.latitude * DEG_TO_RAD
    val phi2 = to.latitude * DEG_TO_RAD
    val dLambda = (to.longitude - from.longitude) * DEG_TO_RAD
    val y = kotlin.math.sin(dLambda) * kotlin.math.cos(phi2)
    val x = kotlin.math.cos(phi1) * kotlin.math.sin(phi2) -
            kotlin.math.sin(phi1) * kotlin.math.cos(phi2) * kotlin.math.cos(dLambda)
    return (kotlin.math.atan2(y, x) / DEG_TO_RAD + 360.0) % 360.0
}

/**
 * Identifies the app to the OpenStreetMap services it queries.
 *
 * This used to read "JustACalculator/1.x (osmdroid)", which was both untrue —
 * these calls are ours, not osmdroid's — and unreachable. OSM's usage policy
 * asks for an agent that names the application and offers a way to make contact,
 * so a service that thinks we are misbehaving can send an email instead of
 * silently blocking the app for every player at once.
 */
private const val MAP_USER_AGENT = "JustACalculator/1.14 (+fictioncutshort@gmail.com)"

/**
 * The dashed line drawn to a destination when no real route is available —
 * always on Android, and on iOS whenever MapKit declines to route.
 *
 * Bowed rather than straight, by an offset proportional to the distance, so it
 * reads as "roughly this way" instead of as a claim about the pavement. The
 * player is meant to find their own way; the line only says which way to set off.
 *
 * A quadratic Bézier, sampled evenly. The control point sits at the midpoint
 * pushed sideways, so the curve always bends the same way relative to the walk.
 */
private fun guessPath(from: GeoPoint, to: GeoPoint, samples: Int = 28): List<GeoPoint> {
    val metres = from.distanceToAsDouble(to)
    if (metres < 1.0) return listOf(from, to)

    // A gentle bow: 12% of the crossing, capped so a long walk does not arc
    // absurdly and a short one still visibly curves.
    val bow = (metres * 0.12).coerceIn(6.0, 60.0)
    val heading = bearingDeg(from, to)
    val mid = from.destinationPoint(metres / 2.0, heading)
    val control = mid.destinationPoint(bow, (heading + 90.0) % 360.0)

    return List(samples + 1) { i ->
        val t = i.toDouble() / samples
        val inv = 1.0 - t
        val a = inv * inv
        val b = 2.0 * inv * t
        val c = t * t
        GeoPoint(
            latitude = a * from.latitude + b * control.latitude + c * to.latitude,
            longitude = a * from.longitude + b * control.longitude + c * to.longitude,
        )
    }
}

/**
 * An arrowhead at the end of [path]: two barbs meeting at the destination,
 * returned as a single barb→tip→barb polyline so it draws in one stroke.
 *
 * Angled off the path's own final heading rather than the straight-line bearing,
 * so it stays aligned with the curve it caps.
 */
private fun arrowHead(path: List<GeoPoint>, lengthM: Double = 16.0, spreadDeg: Double = 30.0): List<GeoPoint> {
    if (path.size < 2) return emptyList()
    val tip = path.last()
    val approach = bearingDeg(path[path.size - 2], tip)
    // Barbs point back down the path, splayed either side of it.
    val back = (approach + 180.0) % 360.0
    return listOf(
        tip.destinationPoint(lengthM, (back - spreadDeg + 360.0) % 360.0),
        tip,
        tip.destinationPoint(lengthM, (back + spreadDeg) % 360.0),
    )
}

/**
 * Run an Overpass query, trying each mirror in turn, or null if they all fail.
 *
 * This used to be a single un-retried call to overpass-api.de whose every failure
 * was swallowed. That endpoint is a free service that rate-limits hard and times
 * out often, and a swallowed failure meant zero road candidates, which sent the
 * caller down its blind-projection path — points invented on a compass bearing,
 * with no idea what's under them. That is how destinations ended up in fields and
 * back gardens. A mirror list plus an honest status check makes the road query
 * actually succeed; nothing else about the pick changes.
 */
private val OVERPASS_ENDPOINTS = listOf(
    "https://overpass-api.de/api/interpreter",
    "https://overpass.kumi.systems/api/interpreter",
    "https://overpass.osm.jp/api/interpreter",
)

/**
 * Last Overpass answer, keyed by the query that produced it.
 *
 * Every round asks about a circle around the player, and a player who has not
 * moved far asks the same question again — on a re-roll, on returning to the
 * chapter, after a skip. Those repeats were all going out to a free community
 * service that rate-limits by volume.
 *
 * Deliberately a single entry keyed on the exact query text, not a real cache:
 * a hit is only possible when the question is character-for-character identical,
 * which means the same rounded position and radius. That cannot change which
 * points come back — it can only avoid asking twice for the same answer. A miss
 * behaves exactly as before.
 *
 * Not persisted. Streets change rarely, but a stale answer surviving a restart
 * would be a behaviour change, and the win here is in the repeats within one
 * sitting.
 */
private var overpassCacheKey: String? = null
private var overpassCacheBody: String? = null

private fun fetchOverpass(query: String): String? {
    overpassCacheBody?.let { if (overpassCacheKey == query) return it }
    val encoded = urlEncode(query)
    for (endpoint in OVERPASS_ENDPOINTS) {
        // 429 (rate limited) and 504 (gateway timeout) are the usual answers
        // from a busy mirror, and reading the body on either used to throw —
        // which is exactly the failure that vanished. Check, then move on.
        val response = httpGetText(
            url = "$endpoint?data=$encoded",
            connectTimeoutMs = 8_000,
            readTimeoutMs = 20_000,
            userAgent = MAP_USER_AGENT,
        ) ?: continue
        if (response.status !in 200..299) continue
        if (response.body.isNotBlank()) {
            // Only a genuine answer is cached. A failure falls through to the
            // next mirror and is never remembered, so reliability is unchanged.
            overpassCacheKey = query
            overpassCacheBody = response.body
            return response.body
        }
    }
    return null
}

/**
 * Pick up to [n] OSM road-vertices 80–120 m from [user], spread out by bearing.
 * Pedestrian-friendly highway types are preferred; residential / unclassified /
 * service used as fallback (covers US-style suburbs without footway tagging).
 * Only [visited] vertices are excluded — every other in-band road/path point is a
 * candidate, so the caller has as many ON-ROAD options as the area offers. May
 * return fewer than [n] only when the area genuinely has fewer streets nearby.
 */
private fun pickWalkableDestinations(
    user: GeoPoint,
    visited: List<GeoPoint>,
    n: Int
): List<GeoPoint> {
    val pedTypes = setOf("footway", "pedestrian", "path", "cycleway", "living_street")
    val privateAccess = setOf("private", "no", "customers", "delivery")
    val noFoot = setOf("no", "private")
    val skipService = setOf("driveway", "parking_aisle")
    val pedestrian = mutableListOf<GeoPoint>()
    val road = mutableListOf<GeoPoint>()

    val query = """
        [out:json][timeout:20];
        way(around:$OVERPASS_RADIUS_M,${user.latitude},${user.longitude})
            ["highway"~"^(footway|pedestrian|path|cycleway|living_street|residential|tertiary|unclassified|service)$"];
        out tags geom;
    """.trimIndent()
    val body = fetchOverpass(query)

    /** Collect every way-vertex whose distance from [user] falls in [minD]..[maxD]. */
    fun harvest(minD: Double, maxD: Double) {
        pedestrian.clear(); road.clear()
        val elements = JsonObj.parse(body ?: return).optJSONArray("elements") ?: return
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
                if (d in minD..maxD) target.add(p)
            }
        }
    }

    try {
        harvest(TARGET_DIST_MIN_M, TARGET_DIST_MAX_M)
        // Nothing in the 80–120 m band doesn't mean there are no streets — it can
        // just mean the nearest junction is 140 m off. Widen the band before
        // giving up, because giving up here is what puts a target in a hedge.
        if (pedestrian.isEmpty() && road.isEmpty()) {
            harvest(TARGET_DIST_MIN_M * 0.6, TARGET_DIST_MAX_M * 1.8)
        }
    } catch (_: Exception) {
        // Malformed response → fall through with whatever we parsed
    }

    fun nearVisited(p: GeoPoint) =
        visited.any { v -> v.distanceToAsDouble(p) < VISITED_BUFFER_M }

    // Keep EVERY walkable road/path vertex in the band (only visited ones drop out).
    // Freshness vs. the carry-overs is the caller's job — here we just want as many
    // real ON-ROAD options as the area actually has, so the round never has to fall
    // back to off-road projections.
    val pedPool = pedestrian.distinctBy(::geoKey).filterNot(::nearVisited)
    val roadPool = road.distinctBy(::geoKey).filterNot(::nearVisited)
    // Prefer pedestrian-friendly ways when there are plenty; otherwise use them all.
    val pool = if (pedPool.size >= n) pedPool else (pedPool + roadPool).distinctBy(::geoKey)
    if (pool.isEmpty()) return emptyList()
    return spreadDistinct(pool, user, n, MIN_SEPARATION_M)
}

/**
 * Farthest-first sampling from [user]: starts with a random pick, then iteratively
 * adds the candidate whose minimum angular separation from the already-picked
 * points is greatest, spreading them across the compass. It first tries to keep
 * every pick at least [minSepM] apart; if it can't reach [n] that way it RELAXES
 * the separation in halves and keeps filling — so it returns as many real
 * ROAD/path points as the area has, rather than stopping short and letting the
 * caller invent off-road targets. Only returns fewer than [n] if the pool itself
 * has fewer points.
 */
private fun spreadDistinct(pool: List<GeoPoint>, user: GeoPoint, n: Int, minSepM: Double): List<GeoPoint> {
    if (pool.isEmpty()) return emptyList()
    val picked = mutableListOf(pool[Random.nextInt(pool.size)])
    var sep = minSepM
    while (picked.size < n && sep >= 4.0) {
        var best: GeoPoint? = null
        var bestScore = -1.0
        for (c in pool) {
            if (picked.any { it.distanceToAsDouble(c) < sep }) continue   // also skips already-picked (dist 0)
            val score = picked.minOf { p -> angularSep(user, c, p) }
            if (score > bestScore) { bestScore = score; best = c }
        }
        if (best != null) picked.add(best) else sep *= 0.5   // nothing far enough apart — loosen and retry
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

private fun saveCaptures(context: AppContext, list: List<PlaceCapture>) {
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

/** Internal, not private: the confrontation reads the player's own captures back
 *  out of [BuildingProgress] to put one of them on screen. They are persisted
 *  whether or not the player ever pressed SAVE, so the picture always exists. */
internal fun loadCaptures(context: AppContext): List<PlaceCapture> {
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
