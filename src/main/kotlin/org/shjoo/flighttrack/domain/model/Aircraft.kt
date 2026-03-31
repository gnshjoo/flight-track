package org.shjoo.flighttrack.domain.model

data class AircraftSnapshot(
    val time: Long,
    val aircraft: List<AircraftState>
)

data class AircraftState(
    val icao24: String,
    val callsign: String,
    val originCountry: String,
    val longitude: Double,
    val latitude: Double,
    val altitude: Double?,
    val velocity: Double?,
    val heading: Double?,
    val verticalRate: Double?
)
