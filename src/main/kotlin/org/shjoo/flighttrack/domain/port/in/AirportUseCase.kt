package org.shjoo.flighttrack.domain.port.`in`

import org.shjoo.flighttrack.domain.model.Airport
import org.shjoo.flighttrack.domain.model.Coordinates

interface GetAirportUseCase {
    fun getAirport(code: String): Airport?
    fun getNearestAirport(at: Coordinates): Airport?
}
