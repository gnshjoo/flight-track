package org.shjoo.flighttrack.application.service

import org.shjoo.flighttrack.domain.model.AircraftInfo
import org.shjoo.flighttrack.domain.model.AircraftSnapshot
import org.shjoo.flighttrack.domain.model.BoundingBox
import org.shjoo.flighttrack.domain.model.Icao24
import org.shjoo.flighttrack.domain.model.Track
import org.shjoo.flighttrack.domain.port.`in`.GetAircraftInfoUseCase
import org.shjoo.flighttrack.domain.port.`in`.GetAircraftUseCase
import org.shjoo.flighttrack.domain.port.`in`.GetTrackUseCase
import org.shjoo.flighttrack.domain.port.out.AircraftMetadataPort
import org.shjoo.flighttrack.domain.port.out.AircraftTrackPort
import org.shjoo.flighttrack.domain.port.out.AircraftTrackingPort
import org.springframework.stereotype.Service

@Service
class AircraftService(
    private val aircraftTrackingPort: AircraftTrackingPort,
    private val aircraftTrackPort: AircraftTrackPort,
    private val aircraftMetadataPort: AircraftMetadataPort
) : GetAircraftUseCase, GetTrackUseCase, GetAircraftInfoUseCase {

    override suspend fun getAircraft(box: BoundingBox?): AircraftSnapshot {
        // 데이터 소스는 원 범위로 조회하므로 bbox 로 한 번 더 트림한다
        val snapshot = aircraftTrackingPort.fetchAircraft(box)
        return box?.let { b -> snapshot.copy(aircraft = snapshot.aircraft.filter { b.contains(it.latitude, it.longitude) }) }
            ?: snapshot
    }

    override suspend fun getAircraftByCallsign(callsign: String): AircraftSnapshot {
        return aircraftTrackingPort.fetchAircraftByCallsign(callsign)
    }

    override suspend fun getTrack(icao24: Icao24): Track {
        return aircraftTrackPort.fetchTrack(icao24)
    }

    override suspend fun getAircraftInfo(icao24: Icao24): AircraftInfo? {
        return aircraftMetadataPort.fetchAircraftMetadata(icao24)
    }
}
