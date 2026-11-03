package org.shjoo.flighttrack.adapter.out.adsblol

import org.shjoo.flighttrack.domain.model.BoundingBox
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TilingTest {

    private fun box(lamin: Double, lomin: Double, lamax: Double, lomax: Double) =
        BoundingBox.fromParams(lamin, lomin, lamax, lomax)!!

    private fun distanceNm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2) * sin(dLat / 2) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
        return 2 * 3440.065 * asin(sqrt(a))
    }

    private fun assertCovered(b: BoundingBox) {
        val set = tilesFor(b)
        assertFalse(set.partial, "expected full coverage for ${b.lamin},${b.lomin}..${b.lamax},${b.lomax}")
        val n = 40
        for (i in 0..n) for (j in 0..n) {
            val lat = b.lamin + (b.lamax - b.lamin) * i / n
            val lon = b.lomin + (b.lomax - b.lomin) * j / n
            val nearest = set.tiles.minOf { t -> t.circle().let { distanceNm(lat, lon, it.lat, it.lon) } }
            assertTrue(nearest <= 250.0, "($lat, $lon) is $nearest nm from the nearest tile centre")
        }
    }

    @Test
    fun `every point of a fully covered view lies inside some tile circle`() {
        assertCovered(box(33.0, 124.0, 39.0, 131.0))
        assertCovered(box(58.0, 5.0, 62.0, 25.0))
        assertCovered(box(70.0, 20.0, 75.0, 40.0))
        assertCovered(box(-4.0, 30.0, 4.0, 40.0))
        assertCovered(box(-20.0, 172.0, -14.0, 180.0))
        assertCovered(box(-20.0, -180.0, -14.0, -172.0))
        assertCovered(box(-75.0, -70.0, -70.0, -50.0))
    }

    @Test
    fun `the Korea view needs at most four tiles and is complete`() {
        val set = tilesFor(box(33.0, 124.0, 39.0, 131.0))
        assertTrue(set.tiles.size <= 4, "got ${set.tiles.size}")
        assertFalse(set.partial)
    }

    @Test
    fun `the East Asia view is capped at six tiles and marked partial`() {
        val set = tilesFor(box(20.0, 100.0, 50.0, 150.0))
        assertEquals(6, set.tiles.size)
        assertTrue(set.partial)
    }

    @Test
    fun `tiles come nearest to the view centre first`() {
        val b = box(20.0, 100.0, 50.0, 150.0)
        val set = tilesFor(b)
        val distances = set.tiles.map { t -> t.circle().let { distanceNm(35.0, 125.0, it.lat, it.lon) } }
        assertEquals(distances.sorted(), distances)
    }

    @Test
    fun `the same view always maps to the same tiles and nearby views share them`() {
        val a = tilesFor(box(33.0, 124.0, 39.0, 131.0))
        assertEquals(a, tilesFor(box(33.0, 124.0, 39.0, 131.0)))
        val panned = tilesFor(box(33.2, 124.3, 39.2, 131.3))
        assertTrue(a.tiles.intersect(panned.tiles.toSet()).isNotEmpty())
    }

    @Test
    fun `bands beyond 75 degrees are skipped and the view is marked partial`() {
        val set = tilesFor(box(70.0, 20.0, 85.0, 40.0))
        assertTrue(set.partial)
        assertTrue(set.tiles.all { it.circle().lat < 75.0 })
    }

    @Test
    fun `no cell is wider than 400 nm in any band`() {
        for (row in -15..14) {
            val equatorwardLat = if (row >= 0) row * 5.0 else (row + 1) * 5.0
            val widthNm = distanceNm(equatorwardLat, 0.0, equatorwardLat, colStepDeg(row).toDouble())
            assertTrue(widthNm <= 400.0, "row $row is $widthNm nm wide")
        }
    }
}
