package org.shjoo.flighttrack.domain.port.out

import org.shjoo.flighttrack.domain.model.AircraftInfo
import org.shjoo.flighttrack.domain.model.AircraftSnapshot
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
