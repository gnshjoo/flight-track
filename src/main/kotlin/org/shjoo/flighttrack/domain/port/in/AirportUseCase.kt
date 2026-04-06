package org.shjoo.flighttrack.domain.port.`in`

import org.shjoo.flighttrack.domain.model.Airport

interface GetAirportUseCase {
    fun getAirport(code: String): Airport?
}
