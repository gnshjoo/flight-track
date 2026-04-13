package org.shjoo.flighttrack.domain.port.`in`

import org.shjoo.flighttrack.domain.model.AircraftInfo
import org.shjoo.flighttrack.domain.model.AircraftSnapshot
import org.shjoo.flighttrack.domain.model.Track

interface GetAircraftUseCase {
    suspend fun getAircraft(lamin: Double?, lomin: Double?, lamax: Double?, lomax: Double?): AircraftSnapshot
    suspend fun getAircraftByCallsign(callsign: String): AircraftSnapshot
}

interface GetTrackUseCase {
    suspend fun getTrack(icao24: String): Track
}

interface GetAircraftInfoUseCase {
    suspend fun getAircraftInfo(icao24: String): AircraftInfo?
}
