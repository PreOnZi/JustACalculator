package com.fictioncutshort.justacalculator.platform

/**
 * Android has no free, keyless walking-directions API.
 *
 * Google's Directions API is billed and needs a key; Mapbox, GraphHopper and
 * OpenRouteService all have usable free tiers but still mean shipping a key that
 * can be pulled out of the APK, and each one adds a third party that has to be
 * named in the privacy policy — all for a decorative line to somewhere within
 * about 250 metres.
 *
 * So Android draws the dashed curve instead. See [platformWalkingRoute].
 */
actual suspend fun platformWalkingRoute(from: GeoPoint, to: GeoPoint): List<GeoPoint>? = null
