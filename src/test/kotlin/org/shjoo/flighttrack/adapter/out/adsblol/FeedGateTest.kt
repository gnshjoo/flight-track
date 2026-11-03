package org.shjoo.flighttrack.adapter.out.adsblol

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FeedGateTest {

    @Test
    fun `the first call does not wait`() {
        assertEquals(0L, FeedGate().reserve(nowMs = 50_000))
    }

    @Test
    fun `a burst of 20 calls starts at least 1200 ms apart and refuses what would wait over 7500 ms`() = runTest {
        val gate = FeedGate(clockMs = { testScheduler.currentTime })
        val starts = (1..20).map { async { gate.paced { testScheduler.currentTime } } }.awaitAll()

        val ran = starts.filterNotNull().sorted()
        assertEquals(listOf(0L, 1_200L, 2_400L, 3_600L, 4_800L, 6_000L, 7_200L), ran)
        assertEquals(13, starts.count { it == null })
    }

    @Test
    fun `a refused call reserves nothing`() {
        val gate = FeedGate()
        repeat(7) { gate.reserve(nowMs = 0) }
        assertNull(gate.reserve(nowMs = 0))
        assertNull(gate.reserve(nowMs = 0))
        assertEquals(7_500L, gate.reserve(nowMs = 900))
    }

    @Test
    fun `the gate recovers after an idle period`() {
        val gate = FeedGate()
        repeat(7) { gate.reserve(nowMs = 0) }
        assertNull(gate.reserve(nowMs = 0))
        assertEquals(0L, gate.reserve(nowMs = 60_000))
        assertTrue(gate.reserve(nowMs = 60_000)!! >= 1_200)
    }
}
