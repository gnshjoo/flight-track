package org.shjoo.flighttrack.domain.port.out

import org.shjoo.flighttrack.domain.model.AircraftInfo
import org.shjoo.flighttrack.domain.model.AircraftSnapshot
import org.shjoo.flighttrack.domain.model.FlightRoute
import org.shjoo.flighttrack.domain.model.Track

interface AircraftTrackingPort {
    /**
     * bbox 범위 내 항공기 조회. 모든 인자가 null 이면 기본 지역(구현체 정의)을 조회한다.
     */
    suspend fun fetchAircraft(
        lamin: Double?,
        lomin: Double?,
        lamax: Double?,
        lomax: Double?
    ): AircraftSnapshot

    /**
     * 콜사인으로 항공기 검색. 정확 매칭 우선, 없으면 부분 매칭(구현체 정의).
     */
    suspend fun fetchAircraftByCallsign(callsign: String): AircraftSnapshot
}

interface AircraftTrackPort {
    suspend fun fetchTrack(icao24: String): Track
}

interface AircraftMetadataPort {
    suspend fun fetchAircraftMetadata(icao24: String): AircraftInfo?
}

interface FlightRoutePort {
    /**
     * Callsign 기반 출발/도착 공항 lookup. lat/lng 는 adsb.lol 같은 노선 DB 가
     * 후보 중 위치로 disambiguate 할 때 참고함.
     *
     * @return null — 편명 비어있거나 노선 DB 에 매칭 없음
     */
    suspend fun resolveRoute(callsign: String, lat: Double?, lng: Double?): FlightRoute?
}
