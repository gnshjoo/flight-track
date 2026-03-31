package org.shjoo.flighttrack.domain.model

data class Airport(
    val name: String,
    val city: String,
    val iata: String,
    val country: String,
    val lat: Double?,
    val lng: Double?
)
