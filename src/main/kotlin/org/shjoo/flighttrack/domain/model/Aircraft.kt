package org.shjoo.flighttrack.domain.model

data class AircraftSnapshot(
    val time: Long,
    val aircraft: List<AircraftState>,
    val partial: Boolean = false,
    val servedAt: Double = time.toDouble()
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
    val verticalRate: Double?,
    val seenAt: Double? = null
)

data class AircraftInfo(
    val icao24: String,
    val registration: String?,
    val manufacturerName: String?,
    val model: String?,
    val operator: String?,
    val owner: String?,
    val built: String?,
    val categoryDescription: String?
)
