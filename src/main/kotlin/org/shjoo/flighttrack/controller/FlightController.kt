package org.shjoo.flighttrack.controller

import org.shjoo.flighttrack.dto.FlightResponse
import org.shjoo.flighttrack.service.AmadeusService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class FlightController(private val amadeusService: AmadeusService) {

    @GetMapping("/flights")
    suspend fun searchFlights(
        @RequestParam from: String,
        @RequestParam dateFrom: String,
        @RequestParam dateTo: String
    ): List<FlightResponse> {
        return amadeusService.searchFlights(
            from = from.uppercase(),
            dateFrom = dateFrom,
            dateTo = dateTo
        )
    }

    @GetMapping("/flights/detail")
    suspend fun getFlightDetail(
        @RequestParam from: String,
        @RequestParam to: String,
        @RequestParam departureDate: String
    ): FlightResponse? {
        return amadeusService.getFlightDetail(
            from = from.uppercase(),
            to = to.uppercase(),
            departureDate = departureDate
        )
    }
}
