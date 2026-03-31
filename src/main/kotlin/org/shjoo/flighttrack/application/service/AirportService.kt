package org.shjoo.flighttrack.application.service

import org.shjoo.flighttrack.domain.model.Airport
import org.shjoo.flighttrack.domain.port.`in`.FindNearestAirportUseCase
import org.shjoo.flighttrack.domain.port.`in`.SearchAirportsUseCase
import org.shjoo.flighttrack.domain.port.out.AirportCoordinatePort
import org.shjoo.flighttrack.domain.port.out.AirportSearchPort
import org.springframework.stereotype.Service

@Service
class AirportService(
    private val airportSearchPort: AirportSearchPort,
    private val airportCoordinatePort: AirportCoordinatePort
) : SearchAirportsUseCase, FindNearestAirportUseCase {

    override suspend fun searchAirports(query: String): List<Airport> {
        return airportSearchPort.searchAirports(query)
    }

    override fun findNearestAirport(lat: Double, lng: Double): Airport? {
        return airportCoordinatePort.findNearestAirport(lat, lng)?.let { (iata, coords) ->
            Airport(
                name = iata,
                city = "",
                iata = iata,
                country = "",
                lat = coords.first,
                lng = coords.second
            )
        }
    }
}
