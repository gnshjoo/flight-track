package org.shjoo.flighttrack.domain.port.out

import org.shjoo.flighttrack.domain.model.AircraftSnapshot
import org.shjoo.flighttrack.domain.model.Track

interface AircraftTrackingPort {
    suspend fun fetchAllAircraft(): AircraftSnapshot
}

interface AircraftTrackPort {
    suspend fun fetchTrack(icao24: String): Track
}
