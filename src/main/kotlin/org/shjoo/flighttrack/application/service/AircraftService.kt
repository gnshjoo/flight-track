package org.shjoo.flighttrack.application.service

import org.shjoo.flighttrack.domain.model.AircraftSnapshot
import org.shjoo.flighttrack.domain.model.Track
import org.shjoo.flighttrack.domain.port.`in`.GetAircraftUseCase
import org.shjoo.flighttrack.domain.port.`in`.GetTrackUseCase
import org.shjoo.flighttrack.domain.port.out.AircraftTrackPort
import org.shjoo.flighttrack.domain.port.out.AircraftTrackingPort
import org.springframework.stereotype.Service

@Service
class AircraftService(
    private val aircraftTrackingPort: AircraftTrackingPort,
    private val aircraftTrackPort: AircraftTrackPort
) : GetAircraftUseCase, GetTrackUseCase {

    override suspend fun getAircraft(
        lamin: Double?,
        lomin: Double?,
        lamax: Double?,
        lomax: Double?
    ): AircraftSnapshot {
        val snapshot = aircraftTrackingPort.fetchAllAircraft()
        return filterByBounds(snapshot, lamin, lomin, lamax, lomax)
    }

    override suspend fun getTrack(icao24: String): Track {
        return aircraftTrackPort.fetchTrack(icao24)
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
