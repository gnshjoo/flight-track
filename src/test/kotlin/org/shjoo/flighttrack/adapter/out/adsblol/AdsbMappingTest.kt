package org.shjoo.flighttrack.adapter.out.adsblol

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import org.shjoo.flighttrack.adapter.out.adsblol.dto.AdsbLolAircraft
import org.shjoo.flighttrack.adapter.out.adsblol.dto.AdsbLolV2Response
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
        ).toState(nowSeconds = 1_000.0)
        assertNotNull(state)
        assertEquals("KAL1", state.callsign)
        assertEquals(3048.0, state.altitude!!, 1e-6)
        assertEquals(51.4444, state.velocity!!, 1e-6)
        assertEquals(5.08, state.verticalRate!!, 1e-6)
    }

    @Test
    fun `ground aircraft and aircraft without a position are omitted from snapshots`() {
        assertNull(AdsbLolAircraft(hex = "a", altBaro = "ground", lat = 37.0, lon = 127.0).toState(1_000.0))
        assertNull(AdsbLolAircraft(hex = "b", altBaro = 5000, lat = null, lon = 127.0).toState(1_000.0))
    }

    @Test
    fun `seenAt is the response time minus seen_pos`() {
        val state = AdsbLolAircraft(hex = "a", altBaro = 30_000, lat = 37.0, lon = 127.0, seenPos = 2.5).toState(nowSeconds = 1_762_160_004.0)
        assertEquals(1_762_160_001.5, state!!.seenAt!!, 1e-9)
    }

    @Test
    fun `seenAt is null when the feed omits seen_pos`() {
        assertNull(AdsbLolAircraft(hex = "a", altBaro = 30_000, lat = 37.0, lon = 127.0).toState(1_762_160_004.0)!!.seenAt)
    }

    @Test
    fun `adsb_fi seconds and adsb_lol millis give the same seenAt`() {
        val record = """{"hex":"71be1d","alt_baro":30000,"lat":37.0,"lon":127.0,"seen_pos":1.2}"""
        val lol = jacksonObjectMapper().readValue<AdsbLolV2Response>("""{"now":1762160004300,"ac":[$record]}""")
        val fi = jacksonObjectMapper().readValue<AdsbLolV2Response>("""{"now":1762160004.3,"aircraft":[$record]}""")
        val lolSeenAt = lol.states!!.single().toState(lol.nowSeconds)!!.seenAt!!
        val fiSeenAt = fi.states!!.single().toState(fi.nowSeconds)!!.seenAt!!
        assertEquals(1_762_160_003.1, lolSeenAt, 1e-3)
        assertEquals(1_762_160_003.1, fiSeenAt, 1e-3)
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
        val parsed = assertNotNull(parseTrace(fixture(), fixtureNow))
        assertEquals(6, parsed.waypoints.size)
        assertTrue(parsed.waypoints.first().onGround)
        assertEquals(35.545074, parsed.waypoints.first().latitude, 1e-9)
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
        assertEquals(6, fresh.waypoints.size)
        assertEquals(fixtureLastPointTime, fresh.waypoints.last().time)
    }

    @Test
    fun `a coverage gap at cruise speed stays one leg`() {
        val before = List(5) { point(it * 10.0, 30.0 + it * 0.01) }
        val after = List(5) { point(7_040.0 + it * 10, 44.4 + it * 0.01) }
        val parsed = assertNotNull(parseTrace(AdsbTraceResponse(timestamp = 1000.0, trace = before + after), nowEpochSeconds = 1000L + 7_100))
        assertEquals(10, parsed.waypoints.size)
        assertEquals(30.0, parsed.waypoints.first().latitude, 1e-9)
    }

    @Test
    fun `a long gap with no movement starts a new leg`() {
        val parked = List(5) { point(it * 10.0, 30.0) }
        val next = List(5) { point(7_040.0 + it * 10, 30.018 + it * 0.01) }
        val parsed = assertNotNull(parseTrace(AdsbTraceResponse(timestamp = 1000.0, trace = parked + next), nowEpochSeconds = 1000L + 7_100))
        assertEquals(5, parsed.waypoints.size)
        assertEquals(30.018, parsed.waypoints.first().latitude, 1e-9)
        assertEquals(1000L + 7_080, parsed.waypoints.last().time)
    }
}
