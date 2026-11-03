package org.shjoo.flighttrack.domain.port.`in`

import org.shjoo.flighttrack.domain.model.AircraftInfo
import org.shjoo.flighttrack.domain.model.AircraftSnapshot
import org.shjoo.flighttrack.domain.model.BoundingBox
import org.shjoo.flighttrack.domain.model.Icao24
import org.shjoo.flighttrack.domain.model.Track

interface GetAircraftUseCase {
    suspend fun getAircraft(box: BoundingBox?): AircraftSnapshot
    suspend fun getAircraftByCallsign(callsign: String): AircraftSnapshot
}

interface GetTrackUseCase {
    suspend fun getTrack(icao24: Icao24): Track
}

interface GetAircraftInfoUseCase {
    suspend fun getAircraftInfo(icao24: Icao24): AircraftInfo?
}
