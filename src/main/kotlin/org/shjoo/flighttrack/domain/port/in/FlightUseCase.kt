package org.shjoo.flighttrack.domain.port.`in`

import org.shjoo.flighttrack.domain.model.Flight

interface SearchFlightsUseCase {
    suspend fun searchFlights(from: String, dateFrom: String, dateTo: String): List<Flight>
}

interface GetFlightDetailUseCase {
    suspend fun getFlightDetail(from: String, to: String, departureDate: String): Flight?
}
