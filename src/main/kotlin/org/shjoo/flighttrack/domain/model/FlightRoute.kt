package org.shjoo.flighttrack.domain.model

data class FlightRoute(
    val departureIata: String,
    val arrivalIata: String,
    val plausible: Boolean = true,
)
