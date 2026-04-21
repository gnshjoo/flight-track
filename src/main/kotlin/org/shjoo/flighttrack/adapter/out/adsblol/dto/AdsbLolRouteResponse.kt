package org.shjoo.flighttrack.adapter.out.adsblol.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

data class AdsbLolRouteRequest(val planes: List<AdsbLolPlaneQuery>)

data class AdsbLolPlaneQuery(
    val callsign: String,
    val lat: Double,
    val lng: Double,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AdsbLolRouteEntry(
    val callsign: String = "",
    @JsonProperty("_airports") val airports: List<AdsbLolAirport>? = null,
    val plausible: Boolean = true,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AdsbLolAirport(
    val iata: String = "",
    val icao: String = "",
    val name: String = "",
    val lat: Double = 0.0,
    val lon: Double = 0.0,
)
