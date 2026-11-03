package org.shjoo.flighttrack.adapter.out.adsblol

import org.shjoo.flighttrack.domain.model.AircraftState
import kotlin.test.Test
import kotlin.test.assertEquals

class AircraftMergeTest {

    private fun plane(icao24: String, seenAt: Double?, latitude: Double = 37.0) = AircraftState(
        icao24 = icao24, callsign = "", originCountry = "", longitude = 127.0, latitude = latitude,
        altitude = null, velocity = null, heading = null, verticalRate = null, seenAt = seenAt
    )

    @Test
    fun `a plane only one feed sees is kept`() {
        val merged = mergeFreshest(listOf(listOf(plane("aaa111", 10.0)), listOf(plane("bbb222", 10.0))))
        assertEquals(setOf("aaa111", "bbb222"), merged.map { it.icao24 }.toSet())
    }

    @Test
    fun `the later position wins whichever feed reported it`() {
        val older = plane("aaa111", seenAt = 100.0, latitude = 37.0)
        val newer = plane("aaa111", seenAt = 101.5, latitude = 37.1)
        assertEquals(37.1, mergeFreshest(listOf(listOf(older), listOf(newer))).single().latitude)
        assertEquals(37.1, mergeFreshest(listOf(listOf(newer), listOf(older))).single().latitude)
    }

    @Test
    fun `a position without seenAt loses to one with it`() {
        val unknown = plane("aaa111", seenAt = null, latitude = 37.0)
        val known = plane("aaa111", seenAt = 1.0, latitude = 37.1)
        assertEquals(37.1, mergeFreshest(listOf(listOf(unknown), listOf(known))).single().latitude)
        assertEquals(37.1, mergeFreshest(listOf(listOf(known), listOf(unknown))).single().latitude)
    }

    @Test
    fun `ties keep the first list`() {
        val lol = plane("aaa111", seenAt = 100.0, latitude = 37.0)
        val fi = plane("aaa111", seenAt = 100.0, latitude = 37.1)
        assertEquals(37.0, mergeFreshest(listOf(listOf(lol), listOf(fi))).single().latitude)
        assertEquals(37.0, mergeFreshest(listOf(listOf(plane("x", null, 37.0)), listOf(plane("x", null, 37.1)))).single().latitude)
    }

    @Test
    fun `107 and 126 planes sharing 105 merge to 128, one entry each`() {
        val shared = (0 until 105).map { "shared$it" }
        val lol = (shared + List(2) { "lolonly$it" }).map { plane(it, 1.0) }
        val fi = (shared + List(21) { "fionly$it" }).map { plane(it, 2.0) }
        val merged = mergeFreshest(listOf(lol, fi))
        assertEquals(128, merged.size)
        assertEquals(128, merged.map { it.icao24 }.toSet().size)
    }
}
