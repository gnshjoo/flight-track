package org.shjoo.flighttrack.domain.model

data class Track(
    val icao24: String,
    val callsign: String,
    val path: List<TrackWaypoint>
)

data class TrackWaypoint(
    val time: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val heading: Double?,
    val onGround: Boolean
)
