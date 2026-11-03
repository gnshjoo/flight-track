package org.shjoo.flighttrack.adapter.out.adsblol

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.shjoo.flighttrack.adapter.out.adsblol.dto.AdsbLolAircraft
import org.shjoo.flighttrack.adapter.out.adsblol.dto.AdsbLolV2Response
import org.shjoo.flighttrack.domain.model.BoundingBox
import org.shjoo.flighttrack.domain.model.UpstreamUnavailableException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class AircraftTilesTest {

    private val korea = BoundingBox.fromParams(33.0, 124.0, 39.0, 131.0)!!

    private fun planesAt(vararg planes: Pair<String, Circle>) = AdsbLolV2Response(
        ac = planes.map { (hex, c) -> AdsbLolAircraft(hex = hex, altBaro = 30_000, lat = c.lat, lon = c.lon, seenPos = 1.0) },
        now = 1_762_160_000_000.0,
    )

    @Test
    fun `one feed failing still returns the other feed's aircraft`() = runTest {
        val tiles = AircraftTiles(
            feeds = listOf({ _ -> null }, { c -> planesAt("fi${c.lat}${c.lon}" to c) }),
            clockMs = { testScheduler.currentTime },
        )
        val snapshot = tiles.snapshot(korea)
        assertEquals(tilesFor(korea).tiles.size, snapshot.aircraft.size)
        assertFalse(snapshot.partial)
    }

    @Test
    fun `both feeds failing for every tile is an upstream outage`() = runTest {
        val tiles = AircraftTiles(feeds = listOf({ _ -> null }, { _ -> null }), clockMs = { testScheduler.currentTime })
        assertFailsWith<UpstreamUnavailableException> { tiles.snapshot(korea) }
    }

    @Test
    fun `a tile with no data marks the snapshot partial`() = runTest {
        val tiles = AircraftTiles(
            feeds = listOf({ c -> if (c.lat > 35) null else planesAt("south" to c) }),
            clockMs = { testScheduler.currentTime },
        )
        val snapshot = tiles.snapshot(korea)
        assertTrue(snapshot.partial)
        assertEquals(setOf("south"), snapshot.aircraft.map { it.icao24 }.toSet())
    }

    @Test
    fun `time is the oldest contributing tile and servedAt is assembly time`() = runTest {
        val tiles = AircraftTiles(feeds = listOf({ c -> planesAt("p" to c) }), clockMs = { testScheduler.currentTime + 1_762_160_000_000 })
        tiles.snapshot(BoundingBox.fromParams(36.0, 125.0, 39.0, 126.0)!!)
        delay(5_000)
        val snapshot = tiles.snapshot(korea)
        assertEquals(1_762_160_000L, snapshot.time)
        assertEquals(1_762_160_005.0, snapshot.servedAt, 1e-3)
    }
}
