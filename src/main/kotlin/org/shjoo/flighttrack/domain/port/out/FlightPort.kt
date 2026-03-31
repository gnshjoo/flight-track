package org.shjoo.flighttrack.domain.port.out

import org.shjoo.flighttrack.domain.model.Flight

interface FlightSearchPort {
    suspend fun searchInspirationFlights(from: String, departureDate: String): List<Flight>
    suspend fun searchFlightOffers(from: String, to: String, departureDate: String): Flight?
}

interface FlightCachePort {
    fun get(key: String): List<Flight>?
    fun put(key: String, data: List<Flight>)
    fun buildKey(from: String, dateFrom: String, dateTo: String): String
}

interface FlightFallbackPort {
    fun getFallback(from: String): List<Flight>
}
