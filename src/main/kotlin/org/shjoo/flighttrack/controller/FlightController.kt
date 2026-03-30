package org.shjoo.flighttrack.controller

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.shjoo.flighttrack.dto.FlightResponse
import org.shjoo.flighttrack.service.AmadeusService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
@Tag(name = "Flights", description = "항공편 검색 API")
class FlightController(private val amadeusService: AmadeusService) {

    @GetMapping("/flights")
    @Operation(summary = "저가 항공편 목록 조회", description = "출발지와 날짜 범위로 저렴한 목적지 목록을 조회합니다")
    suspend fun searchFlights(
        @Parameter(description = "출발 공항 IATA 코드 (예: ICN)", example = "ICN") @RequestParam from: String,
        @Parameter(description = "출발 날짜 (YYYY-MM-DD)", example = "2026-04-01") @RequestParam dateFrom: String,
        @Parameter(description = "종료 날짜 (YYYY-MM-DD)", example = "2026-04-30") @RequestParam dateTo: String
    ): List<FlightResponse> {
        return amadeusService.searchFlights(
            from = from.uppercase(),
            dateFrom = dateFrom,
            dateTo = dateTo
        )
    }

    @GetMapping("/flights/detail")
    @Operation(summary = "특정 노선 상세 조회", description = "출발지-목적지 간 항공편 상세 정보를 조회합니다")
    suspend fun getFlightDetail(
        @Parameter(description = "출발 공항 IATA 코드", example = "ICN") @RequestParam from: String,
        @Parameter(description = "도착 공항 IATA 코드", example = "NRT") @RequestParam to: String,
        @Parameter(description = "출발 날짜 (YYYY-MM-DD)", example = "2026-04-01") @RequestParam departureDate: String
    ): FlightResponse? {
        return amadeusService.getFlightDetail(
            from = from.uppercase(),
            to = to.uppercase(),
            departureDate = departureDate
        )
    }
}
