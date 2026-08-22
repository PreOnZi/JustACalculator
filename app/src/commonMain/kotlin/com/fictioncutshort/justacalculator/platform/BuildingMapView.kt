package com.fictioncutshort.justacalculator.platform

import androidx.compose.runtime.Composable

/**
 * The walking map for building 5.
 *
 * Only the map surface is platform — osmdroid over OSM tiles on Android, MapKit
 * on iOS. Everything around it (destination picking, routing, the console UI)
 * is shared, so this seam is deliberately drawn at the narrowest point: state
 * in, taps out, no map object escaping.
 *
 * @param userAccuracyM radius of the fix's uncertainty, drawn rather than
 *   hidden — a confident dot on the wrong side of the street reads as a bug.
 * @param activeDest the destination the player picked, or null before they
 *   have. Null means every option renders identically; once set, the others dim.
 * @param route the line to the active destination: a real walking route where
 *   the platform supplies one, otherwise the bowed guess computed by the caller.
 * @param routeIsGuess true when [route] is the guess rather than a real route,
 *   so the map dashes it instead of drawing it solid. The distinction is the
 *   honest one — a solid line implies a pavement that was actually looked up.
 * @param routeArrow a barb-tip-barb polyline capping the guess, or empty. Drawn
 *   in the same stroke style as the route it belongs to.
 * @param fitTrigger bumped by the caller to request a re-frame; the map ignores
 *   repeats of the same value so panning is not fought.
 */
@Composable
expect fun PlatformBuildingMapView(
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
)
