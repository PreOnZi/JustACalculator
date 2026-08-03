package com.fictioncutshort.justacalculator

import com.fictioncutshort.justacalculator.platform.GeoPoint
import com.fictioncutshort.justacalculator.platform.destinationPoint
import com.fictioncutshort.justacalculator.platform.distanceToAsDouble
import kotlin.math.abs
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * The map beat picks destinations in an 80–120 m band and spaces them by
 * bearing, so these two functions decide whether the round is playable. A
 * replacement that is a few percent off would still look plausible on screen
 * and quietly push every destination into the wrong street.
 */
class GeoTest {

    private val london = GeoPoint(51.5074, -0.1278)

    private fun assertNear(expected: Double, actual: Double, tolerance: Double, tag: String) {
        assertTrue(abs(expected - actual) < tolerance, "$tag: expected ~$expected, was $actual")
    }

    @Test
    fun distanceToSelfIsZero() {
        assertNear(0.0, london.distanceToAsDouble(london), 1e-6, "self")
    }

    @Test
    fun distanceIsSymmetric() {
        val paris = GeoPoint(48.8566, 2.3522)
        assertNear(
            london.distanceToAsDouble(paris),
            paris.distanceToAsDouble(london),
            1e-6,
            "symmetry",
        )
    }

    /** London–Paris is ~343.5 km great-circle; anything else means a wrong radius. */
    @Test
    fun knownCityPairMatches() {
        val paris = GeoPoint(48.8566, 2.3522)
        assertNear(343_500.0, london.distanceToAsDouble(paris), 1_500.0, "London-Paris")
    }

    /** One degree of latitude is ~111.3 km anywhere. */
    @Test
    fun oneDegreeOfLatitude() {
        val north = GeoPoint(52.5074, -0.1278)
        assertNear(111_300.0, london.distanceToAsDouble(north), 500.0, "1 deg lat")
    }

    /** The pair have to agree: walk 100 m, and you are 100 m away. */
    @Test
    fun destinationPointRoundTripsThroughDistance() {
        for (bearing in listOf(0.0, 45.0, 90.0, 180.0, 270.0, 337.5)) {
            val there = london.destinationPoint(100.0, bearing)
            assertNear(100.0, london.distanceToAsDouble(there), 0.05, "bearing $bearing")
        }
    }

    /** North is +latitude, east is +longitude — a sign flip would mirror the map. */
    @Test
    fun bearingsPointTheRightWay() {
        val north = london.destinationPoint(500.0, 0.0)
        assertTrue(north.latitude > london.latitude, "0 deg should go north")
        assertNear(london.longitude, north.longitude, 1e-6, "0 deg should not move east")

        val east = london.destinationPoint(500.0, 90.0)
        assertTrue(east.longitude > london.longitude, "90 deg should go east")
        assertNear(london.latitude, east.latitude, 1e-3, "90 deg should barely move north")

        val south = london.destinationPoint(500.0, 180.0)
        assertTrue(south.latitude < london.latitude, "180 deg should go south")

        val west = london.destinationPoint(500.0, 270.0)
        assertTrue(west.longitude < london.longitude, "270 deg should go west")
    }

    /** The accuracy disc projects a point due north; it must not drift in longitude. */
    @Test
    fun dueNorthKeepsLongitude() {
        val edge = london.destinationPoint(40.0, 0.0)
        assertNear(london.longitude, edge.longitude, 1e-9, "due north longitude")
    }

    /** Crossing the antimeridian must stay in (-180, 180]. */
    @Test
    fun longitudeStaysNormalised() {
        val nearDateLine = GeoPoint(0.0, 179.999)
        val over = nearDateLine.destinationPoint(1000.0, 90.0)
        assertTrue(over.longitude in -180.0..180.0, "longitude was ${over.longitude}")
        assertTrue(over.longitude < 0, "crossing east of 180 should wrap negative")
    }
}
