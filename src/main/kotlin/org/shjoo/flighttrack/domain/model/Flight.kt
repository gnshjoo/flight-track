package org.shjoo.flighttrack.domain.model

data class Flight(
    val destination: String,
    val iata: String,
    val lat: Double,
    val lng: Double,
    val price: Int,
    val directPrice: Int?,
    val legs: List<Leg>,
    val deepLink: String?
)

data class Leg(
    val from: String,
    val to: String,
    val airline: String,
    val price: Int?,
    val departureTime: String,
    val arrivalTime: String
)
