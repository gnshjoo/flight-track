package org.shjoo.flighttrack.adapter.out.openflights

import org.shjoo.flighttrack.domain.model.TrackWaypoint
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class RouteResolverTest {

    private val resolver = RouteResolver(testData())

    private fun wp(
        time: Long,
        lat: Double,
        lng: Double,
        heading: Double? = null,
        onGround: Boolean = false,
        altitude: Double? = null,
    ) = TrackWaypoint(time = time, latitude = lat, longitude = lng, altitude = altitude, heading = heading, onGround = onGround)

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
        val flightEast = listOf(wp(0, 35.18, 128.94, altitude = 500.0), wp(600, 36.0, 129.5, heading = 80.0))
        val flightWest = listOf(wp(0, 35.18, 128.94, altitude = 500.0), wp(600, 36.0, 129.5, heading = 300.0))
        assertEquals("PUS" to "NRT", resolver.resolveRoute("KAL123", flightEast))
        assertEquals("PUS" to "ICN", resolver.resolveRoute("KAL123", flightWest))
    }

    @Test
    fun `a cruising first waypoint does not pin a departure`() {
        val cruising = listOf(wp(0, 37.47, 126.45, altitude = 10_000.0), wp(600, 37.0, 128.0, heading = 100.0, altitude = 10_000.0))
        assertEquals(null to null, resolver.resolveRoute("KAL123", cruising))
    }

    @Test
    fun `no heading with several destinations gives no arrival`() {
        val noHeading = listOf(wp(0, 35.18, 128.94, onGround = true), wp(600, 36.0, 129.5))
        assertEquals("PUS" to null, resolver.resolveRoute("KAL123", noHeading))
    }

    @Test
    fun `two destinations in nearly the same direction give no arrival`() {
        // (36.5, 118.0) 에서 ICN 은 79°, NRT 는 86° 방향이라 heading 82° 로는 둘을 가를 수 없다
        val ambiguous = listOf(wp(0, 35.18, 128.94, onGround = true), wp(600, 36.5, 118.0, heading = 82.0))
        assertEquals("PUS" to null, resolver.resolveRoute("KAL123", ambiguous))
    }

    @Test
    fun `an ambiguous arrival from the first airport does not fall through to the next one`() {
        // GMP 와 ICN 모두 첫 점 근처이고, GMP 의 PUS·NRT 는 heading 90° 로 가를 수 없다
        val routes = "KE,1,GMP,2,PUS,3,,0,333\nKE,1,GMP,2,NRT,4,,0,333\nKE,1,ICN,1,NRT,4,,0,333\n"
        val twoAirports = RouteResolver(OpenFlightsData.parse(AIRLINES_CSV, routes, AIRPORTS_DAT))
        val flight = listOf(wp(0, 37.55, 126.75, altitude = 500.0), wp(600, 36.5, 118.0, heading = 90.0))
        assertEquals("GMP" to null, twoAirports.resolveRoute("KAL123", flight))
    }

    @Test
    fun `unknown airline returns departure only`() {
        val result = resolver.resolveRoute("ZZZ999", listOf(wp(0, 37.47, 126.45, altitude = 500.0), wp(600, 37.0, 128.0, heading = 100.0)))
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
