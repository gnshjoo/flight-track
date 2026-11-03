package org.shjoo.flighttrack.adapter.out.adsblol

import org.shjoo.flighttrack.adapter.out.adsblol.dto.AdsbLolAircraft
import org.shjoo.flighttrack.adapter.out.adsblol.dto.AdsbTraceResponse
import org.shjoo.flighttrack.domain.model.AircraftState
import org.shjoo.flighttrack.domain.model.BoundingBox
import org.shjoo.flighttrack.domain.model.TrackWaypoint
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

// readsb 응답 단위(ft, knot, ft/min) → 기존 API 계약 단위(m, m/s) 변환 계수
internal const val FT_TO_M = 0.3048
internal const val KT_TO_MS = 0.514444
internal const val FPM_TO_MS = 0.00508
internal const val MAX_RADIUS_NM = 250
private const val EARTH_RADIUS_NM = 3440.065

internal fun AdsbLolAircraft.toState(): AircraftState? {
    val latitude = lat ?: return null
    val longitude = lon ?: return null
    if (altBaro == "ground") return null
    return AircraftState(
        icao24 = hex,
        callsign = flight?.trim() ?: "",
        originCountry = "",
        longitude = longitude,
        latitude = latitude,
        altitude = (altBaro as? Number)?.toDouble()?.times(FT_TO_M),
        velocity = gs?.times(KT_TO_MS),
        heading = track,
        verticalRate = (baroRate ?: geomRate)?.times(FPM_TO_MS)
    )
}

internal data class Circle(val lat: Double, val lon: Double, val radiusNm: Int)

internal fun BoundingBox.toCircle(): Circle {
    val centerLat = (lamin + lamax) / 2
    val centerLon = (lomin + lomax) / 2
    val radius = haversineNm(centerLat, centerLon, lamax, lomax)
    return Circle(centerLat, centerLon, radius.toInt().coerceIn(1, MAX_RADIUS_NM))
}

private fun haversineNm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
    val dLat = Math.toRadians(lat2 - lat1)
    val dLon = Math.toRadians(lon2 - lon1)
    val a = sin(dLat / 2) * sin(dLat / 2) +
        cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2) * sin(dLon / 2)
    return 2 * EARTH_RADIUS_NM * asin(sqrt(a))
}

internal data class ParsedTrace(val waypoints: List<TrackWaypoint>, val callsign: String?)

/**
 * tar1090 trace 에서 현재 비행 구간(flags 비트 2 = 새 leg 시작, 마지막 마커 이후)만 잘라
 * waypoint 로 변환한다. 쓸 수 있는 점이 없으면 null.
 */
internal fun parseTrace(response: AdsbTraceResponse, maxPoints: Int = 600): ParsedTrace? {
    val trace = response.trace
    if (trace.isNullOrEmpty()) return null

    val lastLegStart = trace.indexOfLast { p ->
        (((p.getOrNull(6) as? Number)?.toInt() ?: 0) and 2) != 0
    }.coerceAtLeast(0)
    val leg = trace.subList(lastLegStart, trace.size)
    val step = if (leg.size > maxPoints) leg.size / maxPoints + 1 else 1

    val waypoints = leg.filterIndexed { i, _ -> i % step == 0 || i == leg.size - 1 }
        .mapNotNull { p ->
            val lat = (p.getOrNull(1) as? Number)?.toDouble() ?: return@mapNotNull null
            val lon = (p.getOrNull(2) as? Number)?.toDouble() ?: return@mapNotNull null
            val alt = p.getOrNull(3)
            TrackWaypoint(
                time = (response.timestamp + ((p.getOrNull(0) as? Number)?.toDouble() ?: 0.0)).toLong(),
                latitude = lat,
                longitude = lon,
                altitude = (alt as? Number)?.toDouble()?.times(FT_TO_M),
                heading = (p.getOrNull(5) as? Number)?.toDouble(),
                onGround = alt == "ground"
            )
        }
    if (waypoints.isEmpty()) return null

    val callsign = leg.mapNotNull { p -> ((p.getOrNull(8) as? Map<*, *>)?.get("flight") as? String)?.trim()?.ifBlank { null } }
        .lastOrNull()
    return ParsedTrace(waypoints, callsign)
}
