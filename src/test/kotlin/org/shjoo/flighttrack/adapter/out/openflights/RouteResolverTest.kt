package org.shjoo.flighttrack.adapter.out.openflights

import org.shjoo.flighttrack.domain.model.TrackWaypoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RouteResolverTest {

    private val resolver = RouteResolver(testData())

    private fun wp(time: Long, lat: Double, lng: Double, heading: Double? = null, onGround: Boolean = false) =
        TrackWaypoint(time = time, latitude = lat, longitude = lng, altitude = null, heading = heading, onGround = onGround)

    @Test
    fun `ground waypoint near an airport decides the departure`() {
        val result = resolver.resolveRoute(
            "KAL123",
            listOf(wp(0, 37.47, 126.45, onGround = true), wp(600, 37.0, 128.0, heading = 100.0))
        )
        assertEquals("ICN", result.first)
    }

    @Test
    fun `several destinations pick the one in the heading direction`() {
        val flightEast = listOf(wp(0, 35.18, 128.94), wp(600, 36.0, 129.5, heading = 80.0))
        val flightWest = listOf(wp(0, 35.18, 128.94), wp(600, 36.0, 129.5, heading = 300.0))
        assertEquals("PUS" to "NRT", resolver.resolveRoute("KAL123", flightEast))
        assertEquals("PUS" to "ICN", resolver.resolveRoute("KAL123", flightWest))
    }

    @Test
    fun `unknown airline returns departure only`() {
        val result = resolver.resolveRoute("ZZZ999", listOf(wp(0, 37.47, 126.45), wp(600, 37.0, 128.0, heading = 100.0)))
        assertEquals("ICN", result.first)
        assertNull(result.second)
    }

    @Test
    fun `empty waypoints resolve to nothing`() {
        assertEquals(null to null, resolver.resolveRoute("KAL123", emptyList()))
    }

    @Test
    fun `airport lookup is case-insensitive`() {
        assertEquals("Incheon International Airport", resolver.getAirport("icn")?.name)
        assertNull(resolver.getAirport("XXX"))
    }

    @Test
    fun `nearest airport returns null outside the search radius`() {
        assertNull(resolver.findNearestAirport(0.0, -150.0, 4.0))
    }

    @Test
    fun `nearest airport for Incheon coordinates is ICN`() {
        val bundled = RouteResolver(OpenFlightsData.loadBundled())
        assertEquals("ICN", bundled.findNearestAirport(37.46, 126.44, 4.0)?.iata)
    }
}
