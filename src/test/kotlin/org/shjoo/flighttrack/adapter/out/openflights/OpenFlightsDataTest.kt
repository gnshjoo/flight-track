package org.shjoo.flighttrack.adapter.out.openflights

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class OpenFlightsDataTest {

    private val data = testData()

    @Test
    fun `airline rows without an iata or icao code are skipped`() {
        assertEquals(mapOf("KAL" to "KE", "AAR" to "OZ"), data.airlineIataByIcao)
        assertEquals("Korean Air", data.airlineNameByIcao["KAL"])
    }

    @Test
    fun `route rows with null markers or blank fields are skipped`() {
        assertEquals(listOf("NRT"), data.destinationsByRoute[RouteKey("KE", "ICN")])
        assertEquals(listOf("ICN", "NRT"), data.destinationsByRoute[RouteKey("KE", "PUS")])
        assertEquals(3, data.destinationsByRoute.size)
    }

    @Test
    fun `quoted commas in airport names do not split columns`() {
        val airport = data.airports.getValue("CMA")
        assertEquals("Comma, Field", airport.name)
        assertEquals(10.5, airport.lat)
        assertEquals(20.5, airport.lng)
    }

    @Test
    fun `airports without coordinates or iata code are excluded`() {
        assertFalse("NOC" in data.airports)
        assertEquals(setOf("ICN", "GMP", "PUS", "NRT", "CMA"), data.airports.keys)
    }

    @Test
    fun `bundled resources load with thousands of airports and routes`() {
        val bundled = OpenFlightsData.loadBundled()
        assertTrue(bundled.airports.size > 5000)
        assertTrue(bundled.destinationsByRoute.size > 10000)
        assertTrue(bundled.airlineIataByIcao.size > 500)
        assertEquals("ICN", bundled.airports["ICN"]?.iata)
        assertNull(bundled.airports["???"])
    }
}
