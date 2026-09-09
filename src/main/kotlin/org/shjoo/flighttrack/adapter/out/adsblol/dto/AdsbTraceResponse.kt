package org.shjoo.flighttrack.adapter.out.adsblol.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

/**
 * tar1090 globe trace 파일 (globe.airplanes.live / adsb.lol).
 * trace 항목: [초 오프셋, lat, lon, alt_baro(ft|"ground"|null), gs, track, flags, baro_rate, ...]
 * flags 비트 2 = 새 비행 leg 시작 마커.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
data class AdsbTraceResponse(
    val icao: String = "",
    val timestamp: Double = 0.0,
    val trace: List<List<Any?>>? = null
)
