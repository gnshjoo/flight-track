package org.shjoo.flighttrack.domain.port.out

import org.shjoo.flighttrack.domain.model.AircraftInfo
import org.shjoo.flighttrack.domain.model.AircraftSnapshot
import org.shjoo.flighttrack.domain.model.FlightRoute
import org.shjoo.flighttrack.domain.model.Track

interface AircraftTrackingPort {
    suspend fun fetchAllAircraft(): AircraftSnapshot
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
