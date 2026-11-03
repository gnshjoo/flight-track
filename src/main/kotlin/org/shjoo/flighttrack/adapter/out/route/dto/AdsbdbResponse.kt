package org.shjoo.flighttrack.adapter.out.route.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

// 모르는 편명은 404 + {"response":"unknown callsign"} 이라 여기까지 오지 않는다.
@JsonIgnoreProperties(ignoreUnknown = true)
data class AdsbdbResponse(val response: AdsbdbBody? = null)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AdsbdbBody(val flightroute: AdsbdbFlightRoute? = null)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AdsbdbFlightRoute(
    val callsign: String = "",
    val origin: AdsbdbAirport? = null,
    val destination: AdsbdbAirport? = null,
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AdsbdbAirport(
    @JsonProperty("iata_code") val iataCode: String = "",
    @JsonProperty("icao_code") val icaoCode: String = "",
)
