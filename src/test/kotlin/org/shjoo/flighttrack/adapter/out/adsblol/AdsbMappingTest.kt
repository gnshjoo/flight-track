package org.shjoo.flighttrack.adapter.out.adsblol

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.shjoo.flighttrack.adapter.out.adsblol.dto.AdsbLolAircraft
import org.shjoo.flighttrack.adapter.out.adsblol.dto.AdsbTraceResponse
import org.shjoo.flighttrack.domain.model.BoundingBox
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AdsbMappingTest {

    private fun fixture(): AdsbTraceResponse =
        jacksonObjectMapper().readValue(javaClass.getResourceAsStream("/adsblol/trace_780ad2.json")!!)

    private val fixtureLastPointTime = 1_793_672_449L
    private val fixtureNow = fixtureLastPointTime + 600

    private fun point(offset: Double, lat: Double?, flags: Int = 0, extra: Any? = null): List<Any?> =
        listOf(offset, lat, 120.0, 30000, 400.0, 90.0, flags, 0, extra)

    @Test
    fun `altitude feet and speed knots convert to metres and metres per second`() {
        val state = AdsbLolAircraft(
            hex = "abc123", flight = "KAL1  ", altBaro = 10_000, gs = 100.0, baroRate = 1000.0, lat = 37.0, lon = 127.0
        ).toState()
        assertNotNull(state)
        assertEquals("KAL1", state.callsign)
        assertEquals(3048.0, state.altitude!!, 1e-6)
        assertEquals(51.4444, state.velocity!!, 1e-6)
        assertEquals(5.08, state.verticalRate!!, 1e-6)
    }

    @Test
    fun `ground aircraft and aircraft without a position are omitted from snapshots`() {
        assertNull(AdsbLolAircraft(hex = "a", altBaro = "ground", lat = 37.0, lon = 127.0).toState())
        assertNull(AdsbLolAircraft(hex = "b", altBaro = 5000, lat = null, lon = 127.0).toState())
    }

    @Test
    fun `bbox converts to a circle centred on the box`() {
        val circle = BoundingBox.fromParams(35.0, 126.0, 37.0, 128.0)!!.toCircle()
        assertEquals(36.0, circle.lat, 1e-9)
        assertEquals(127.0, circle.lon, 1e-9)
        assertTrue(circle.radiusNm in 75..79)
    }

    @Test
    fun `circle radius is clamped to 250 nm and at least 1 nm`() {
        assertEquals(250, BoundingBox.fromParams(20.0, 100.0, 50.0, 150.0)!!.toCircle().radiusNm)
        assertEquals(1, BoundingBox.fromParams(36.0, 127.0, 36.0, 127.0)!!.toCircle().radiusNm)
    }

    @Test
    fun `trace uses only the last leg`() {
        // fixture 는 NRT 지상 이동과 순항 사이 2.6시간이 잘려 있어, 그 공백 이후 3점이 마지막 leg 가 된다
        val parsed = assertNotNull(parseTrace(fixture(), fixtureNow))
        assertEquals(3, parsed.waypoints.size)
        assertFalse(parsed.waypoints.first().onGround)
        assertEquals(36.95177, parsed.waypoints.first().latitude, 1e-9)
        assertEquals(36.917313, parsed.waypoints.last().latitude, 1e-9)
        assertEquals(32100 * 0.3048, parsed.waypoints.last().altitude!!, 1e-6)
    }

    @Test
    fun `trace callsign comes from the last leg not an earlier one`() {
        assertEquals("CCA184", parseTrace(fixture(), fixtureNow)?.callsign)
    }

    @Test
    fun `long traces are downsampled but keep the final point`() {
        val trace = List(1500) { point(it.toDouble(), 30.0 + it * 0.001) }
        val parsed = assertNotNull(parseTrace(AdsbTraceResponse(timestamp = 1000.0, trace = trace), nowEpochSeconds = 2500, maxPoints = 600))
        assertTrue(parsed.waypoints.size <= 600)
        assertTrue(parsed.waypoints.size < 1500)
        assertEquals(30.0 + 1499 * 0.001, parsed.waypoints.last().latitude, 1e-9)
        assertEquals(1000L + 1499, parsed.waypoints.last().time)
    }

    @Test
    fun `trace with no valid points is no trace`() {
        assertNull(parseTrace(AdsbTraceResponse(timestamp = 1000.0, trace = listOf(point(0.0, null), point(1.0, null))), nowEpochSeconds = 1000))
        assertNull(parseTrace(AdsbTraceResponse(timestamp = 1000.0, trace = emptyList()), nowEpochSeconds = 1000))
        assertNull(parseTrace(AdsbTraceResponse(timestamp = 1000.0, trace = null), nowEpochSeconds = 1000))
    }

    @Test
    fun `trace without any callsign object has no callsign`() {
        val parsed = assertNotNull(parseTrace(AdsbTraceResponse(timestamp = 1000.0, trace = listOf(point(0.0, 37.0), point(1.0, 37.1))), nowEpochSeconds = 1000))
        assertNull(parsed.callsign)
        assertFalse(parsed.waypoints.any { it.onGround })
    }

    @Test
    fun `a trace whose last point is older than two hours is discarded`() {
        assertNull(parseTrace(fixture(), nowEpochSeconds = fixtureLastPointTime + 3 * 3600))
        val fresh = assertNotNull(parseTrace(fixture(), nowEpochSeconds = fixtureLastPointTime + 600))
        assertEquals(3, fresh.waypoints.size)
        assertEquals(fixtureLastPointTime, fresh.waypoints.last().time)
    }

    @Test
    fun `a time gap of more than thirty minutes starts a new leg`() {
        val earlier = List(5) { point(it * 10.0, 30.0 + it * 0.01) }
        val later = List(5) { point(10_000.0 + it * 10, 40.0 + it * 0.01) }
        val parsed = assertNotNull(parseTrace(AdsbTraceResponse(timestamp = 1000.0, trace = earlier + later), nowEpochSeconds = 1000L + 10_100))
        assertEquals(5, parsed.waypoints.size)
        assertEquals(40.0, parsed.waypoints.first().latitude, 1e-9)
        assertEquals(1000L + 10_040, parsed.waypoints.last().time)
    }
}
