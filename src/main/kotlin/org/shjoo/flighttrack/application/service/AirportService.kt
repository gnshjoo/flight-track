package org.shjoo.flighttrack.application.service

import org.shjoo.flighttrack.adapter.out.openflights.RouteResolver
import org.shjoo.flighttrack.domain.model.Airport
import org.shjoo.flighttrack.domain.model.Coordinates
import org.shjoo.flighttrack.domain.port.`in`.GetAirportUseCase
import org.springframework.stereotype.Service

private const val NEAREST_AIRPORT_MAX_DIST_SQ = 4.0

@Service
class AirportService(
    private val routeResolver: RouteResolver
) : GetAirportUseCase {

    override fun getAirport(code: String): Airport? {
        return routeResolver.getAirport(code)
    }

    override fun getNearestAirport(at: Coordinates): Airport? {
        return routeResolver.findNearestAirport(at.lat, at.lng, NEAREST_AIRPORT_MAX_DIST_SQ)
    }
}
