package org.shjoo.flighttrack.adapter.`in`.web

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.shjoo.flighttrack.domain.model.Airport
import org.shjoo.flighttrack.domain.model.Coordinates
import org.shjoo.flighttrack.domain.port.`in`.GetAirportUseCase
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/airports")
@Tag(name = "Airport", description = "공항 정보 API")
class AirportController(
    private val getAirportUseCase: GetAirportUseCase
) {

    @GetMapping("/nearest")
    @Operation(summary = "가장 가까운 공항 조회", description = "좌표 기준 약 2도 이내에서 가장 가까운 공항을 조회합니다. 없으면 404")
    fun getNearest(
        @Parameter(description = "위도", example = "37.46") @RequestParam lat: Double,
        @Parameter(description = "경도", example = "126.44") @RequestParam lng: Double
    ): ResponseEntity<Airport> {
        val airport = getAirportUseCase.getNearestAirport(Coordinates.of(lat, lng))
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(airport)
    }

    @GetMapping("/{code}")
    @Operation(summary = "공항 정보 조회", description = "IATA 코드로 공항 정보를 조회합니다")
    fun getAirport(
        @Parameter(description = "IATA 공항 코드", example = "ICN") @PathVariable code: String
    ): ResponseEntity<Airport> {
        val airport = getAirportUseCase.getAirport(code)
            ?: return ResponseEntity.notFound().build()
        return ResponseEntity.ok(airport)
    }
}
