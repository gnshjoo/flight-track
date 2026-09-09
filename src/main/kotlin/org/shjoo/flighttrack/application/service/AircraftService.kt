package org.shjoo.flighttrack.application.service

import org.shjoo.flighttrack.domain.model.AircraftInfo
import org.shjoo.flighttrack.domain.model.AircraftSnapshot
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

    override suspend fun getAircraft(
        lamin: Double?,
        lomin: Double?,
        lamax: Double?,
        lomax: Double?
    ): AircraftSnapshot {
        // 데이터 소스는 원 범위로 조회하므로 bbox 로 한 번 더 트림한다
        val snapshot = aircraftTrackingPort.fetchAircraft(lamin, lomin, lamax, lomax)
        return filterByBounds(snapshot, lamin, lomin, lamax, lomax)
    }

    override suspend fun getAircraftByCallsign(callsign: String): AircraftSnapshot {
        return aircraftTrackingPort.fetchAircraftByCallsign(callsign)
    }

    override suspend fun getTrack(icao24: String): Track {
        return aircraftTrackPort.fetchTrack(icao24)
    }

    override suspend fun getAircraftInfo(icao24: String): AircraftInfo? {
        return aircraftMetadataPort.fetchAircraftMetadata(icao24)
    }

    private fun filterByBounds(
        data: AircraftSnapshot,
        lamin: Double?, lomin: Double?, lamax: Double?, lomax: Double?
    ): AircraftSnapshot {
        if (lamin == null || lomin == null || lamax == null || lomax == null) return data
        val filtered = data.aircraft.filter { ac ->
            ac.latitude in lamin..lamax && ac.longitude in lomin..lomax
        }
        return AircraftSnapshot(time = data.time, aircraft = filtered)
    }
}
