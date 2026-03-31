package org.shjoo.flighttrack.adapter.`in`.web

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.shjoo.flighttrack.domain.model.Airport
import org.shjoo.flighttrack.domain.port.`in`.FindNearestAirportUseCase
import org.shjoo.flighttrack.domain.port.`in`.SearchAirportsUseCase
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
@Tag(name = "Airports", description = "공항 검색 API")
class AirportController(
    private val searchAirportsUseCase: SearchAirportsUseCase,
    private val findNearestAirportUseCase: FindNearestAirportUseCase
) {

    @GetMapping("/airports")
    @Operation(summary = "공항 검색", description = "키워드로 공항을 검색합니다 (최소 2자 이상)")
    suspend fun searchAirports(
        @Parameter(description = "검색 키워드 (도시명 또는 공항명)", example = "seoul") @RequestParam query: String
    ): List<Airport> {
        if (query.length < 2) return emptyList()

        return try {
            searchAirportsUseCase.searchAirports(query)
        } catch (e: Exception) {
            emptyList()
        }
    }

    @GetMapping("/airports/nearest")
    @Operation(summary = "가장 가까운 공항 조회", description = "좌표 기준 약 100km 이내의 가장 가까운 공항을 반환합니다")
    fun nearestAirport(
        @Parameter(description = "위도", example = "37.4") @RequestParam lat: Double,
        @Parameter(description = "경도", example = "126.4") @RequestParam lng: Double
    ): Airport? {
        return findNearestAirportUseCase.findNearestAirport(lat, lng)
    }
}
