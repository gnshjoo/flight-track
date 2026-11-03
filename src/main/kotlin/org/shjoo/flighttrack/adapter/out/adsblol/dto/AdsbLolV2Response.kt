package org.shjoo.flighttrack.adapter.out.adsblol.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class AdsbLolV2Response(
    val ac: List<AdsbLolAircraft>? = null,
    // adsb.fi 는 동일 포맷을 "aircraft" 키로 반환
    val aircraft: List<AdsbLolAircraft>? = null,
    // adsb.lol 은 epoch ms, adsb.fi 는 epoch 초(소수점 포함)
    val now: Double = 0.0
) {
    val states: List<AdsbLolAircraft>? get() = ac ?: aircraft
    val nowSeconds: Double get() = if (now > 1e12) now / 1000 else now
    val epochSeconds: Long get() = nowSeconds.toLong()
}

@JsonIgnoreProperties(ignoreUnknown = true)
data class AdsbLolAircraft(
    val hex: String = "",
    val flight: String? = null,
    @JsonProperty("r") val registration: String? = null,
    @JsonProperty("t") val typeCode: String? = null,
    // 피트 단위 숫자 또는 지상일 때 문자열 "ground"
    @JsonProperty("alt_baro") val altBaro: Any? = null,
    val gs: Double? = null,
    val track: Double? = null,
    @JsonProperty("baro_rate") val baroRate: Double? = null,
    @JsonProperty("geom_rate") val geomRate: Double? = null,
    val lat: Double? = null,
    val lon: Double? = null,
    // 응답 시각(now) 기준 위치 수신 후 경과 초
    @JsonProperty("seen_pos") val seenPos: Double? = null
)
