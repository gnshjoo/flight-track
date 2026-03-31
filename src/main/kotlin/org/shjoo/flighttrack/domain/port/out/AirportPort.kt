package org.shjoo.flighttrack.domain.port.out

import org.shjoo.flighttrack.domain.model.Airport

interface AirportSearchPort {
    suspend fun searchAirports(query: String): List<Airport>
}

interface AirportCoordinatePort {
    fun findNearestAirport(lat: Double, lng: Double): Pair<String, Pair<Double, Double>>?
}
