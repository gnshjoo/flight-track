package org.shjoo.flighttrack.application.service

import org.shjoo.flighttrack.adapter.out.openflights.RouteResolver
import org.shjoo.flighttrack.domain.model.Airport
import org.shjoo.flighttrack.domain.port.`in`.GetAirportUseCase
import org.springframework.stereotype.Service

@Service
class AirportService(
    private val routeResolver: RouteResolver
) : GetAirportUseCase {

    override fun getAirport(code: String): Airport? {
        return routeResolver.getAirport(code)
    }
}
