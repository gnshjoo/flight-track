package org.shjoo.flighttrack.adapter.out.opensky.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class OpenSkyStatesResponse(
    val time: Long = 0,
    val states: List<List<Any?>>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OpenSkyTrackResponse(
    val icao24: String = "",
    val callsign: String? = null,
    val startTime: Long = 0,
    val endTime: Long = 0,
    val path: List<List<Any?>>? = null
)
