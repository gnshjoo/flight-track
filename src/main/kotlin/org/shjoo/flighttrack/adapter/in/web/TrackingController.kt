package org.shjoo.flighttrack.adapter.`in`.web

import io.swagger.v3.oas.annotations.Operation
import io.swagger.v3.oas.annotations.Parameter
import io.swagger.v3.oas.annotations.tags.Tag
import org.shjoo.flighttrack.domain.model.AircraftSnapshot
import org.shjoo.flighttrack.domain.model.Track
import org.shjoo.flighttrack.domain.port.`in`.GetAircraftUseCase
import org.shjoo.flighttrack.domain.port.`in`.GetTrackUseCase
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/tracking")
@Tag(name = "Tracking", description = "실시간 항공기 추적 API")
class TrackingController(
    private val getAircraftUseCase: GetAircraftUseCase,
    private val getTrackUseCase: GetTrackUseCase
) {

    @GetMapping("/aircraft")
    @Operation(summary = "실시간 항공기 위치 조회", description = "OpenSky Network에서 실시간 항공기 위치를 조회합니다. 30초 캐시 적용")
    suspend fun getAircraft(
        @Parameter(description = "최소 위도") @RequestParam(required = false) lamin: Double?,
        @Parameter(description = "최소 경도") @RequestParam(required = false) lomin: Double?,
        @Parameter(description = "최대 위도") @RequestParam(required = false) lamax: Double?,
        @Parameter(description = "최대 경도") @RequestParam(required = false) lomax: Double?
    ): AircraftSnapshot {
        return getAircraftUseCase.getAircraft(lamin, lomin, lamax, lomax)
    }

    @GetMapping("/track")
    @Operation(summary = "항공기 비행 경로 조회", description = "특정 항공기의 비행 경로(waypoint)를 조회합니다. 30초 캐시 적용")
    suspend fun getTrack(
        @Parameter(description = "항공기 ICAO24 hex 코드", example = "abc123") @RequestParam icao24: String
    ): Track {
        return getTrackUseCase.getTrack(icao24)
    }
}
