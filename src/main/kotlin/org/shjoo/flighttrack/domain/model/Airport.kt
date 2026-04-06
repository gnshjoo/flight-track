package org.shjoo.flighttrack.domain.model

data class Airport(
    val iata: String,
    val name: String,
    val lat: Double,
    val lng: Double
)
