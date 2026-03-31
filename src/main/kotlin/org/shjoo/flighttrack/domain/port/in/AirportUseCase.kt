package org.shjoo.flighttrack.domain.port.`in`

import org.shjoo.flighttrack.domain.model.Airport

interface SearchAirportsUseCase {
    suspend fun searchAirports(query: String): List<Airport>
}

interface FindNearestAirportUseCase {
    fun findNearestAirport(lat: Double, lng: Double): Airport?
}
