package com.fictioncutshort.justacalculator.platform

/**
 * A walking route between two points, when the platform can supply one for free.
 *
 * This used to be a call to `router.project-osrm.org`, whose own terms rule out
 * production use — the code carried a note to that effect for months. It was
 * also the second of only two things in the whole app that sent the player's
 * precise coordinates to a third party, so removing it shortens the privacy
 * policy as well as the risk.
 *
 * What replaced it differs by platform, deliberately:
 *
 *  - **iOS** returns a real route from MapKit's own directions service. It costs
 *    nothing, needs no API key, and adds no new disclosure — the map on iOS is
 *    an `MKMapView`, so Apple already receives the location it is drawing.
 *  - **Android** returns null. There is no free native routing there, and the
 *    alternatives all mean an API key in the bundle plus another third party in
 *    the privacy policy, for a line the player can walk without.
 *
 * Null is a normal answer, not a failure. The map beat draws its own dashed
 * curve to the destination whenever this returns nothing, so the chapter looks
 * and plays the same either way — iOS just gets a route that follows streets.
 */
expect suspend fun platformWalkingRoute(from: GeoPoint, to: GeoPoint): List<GeoPoint>?
