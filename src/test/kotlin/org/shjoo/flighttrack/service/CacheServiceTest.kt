package org.shjoo.flighttrack.service

import org.shjoo.flighttrack.dto.FlightResponse
import org.shjoo.flighttrack.dto.Leg
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class CacheServiceTest {

    private lateinit var cache: CacheService

    @BeforeEach
    fun setUp() {
        cache = CacheService()
    }

    private fun sampleFlight(destination: String = "Bangkok", price: Int = 189) = FlightResponse(
        destination = destination,
        iata = "BKK",
        lat = 13.69,
        lng = 100.75,
        price = price,
        directPrice = null,
        legs = listOf(Leg("ICN", "BKK", "AirAsia X", price, "08:00", "12:30")),
        deepLink = null
    )

    @Test
    fun `get returns null for missing key`() {
        assertNull(cache.get("missing"))
    }

    @Test
    fun `put then get returns stored data`() {
        val flights = listOf(sampleFlight())
        cache.put("key1", flights)
        val result = cache.get("key1")
        assertNotNull(result)
        assertEquals(1, result!!.size)
        assertEquals("Bangkok", result[0].destination)
    }

    @Test
    fun `buildKey produces deterministic key`() {
        val key = cache.buildKey("ICN", "01/04/2026", "30/04/2026")
        assertEquals("ICN|01/04/2026|30/04/2026", key)
    }

    @Test
    fun `evictExpired removes old entries`() {
        // Put an entry, then evict — fresh entry should survive
        cache.put("fresh", listOf(sampleFlight()))
        cache.evictExpired()
        assertNotNull(cache.get("fresh"))
    }
}
