package org.shjoo.flighttrack.adapter.out.openflights

import org.shjoo.flighttrack.domain.model.Airport
import org.shjoo.flighttrack.domain.model.TrackWaypoint
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class RouteResolver(private val data: OpenFlightsData) {

    private val log = LoggerFactory.getLogger(RouteResolver::class.java)

    fun getAirport(code: String): Airport? = data.airports[code.uppercase()]

    /**
     * callsign + waypoints → (departure, arrival)
     *
     * 1단계: onGround waypoint로 출발/도착 공항 식별
     * 2단계: route DB로 노선 검증 및 도착지 추정
     * 3단계: 양쪽 끝점 교차 검증 (지상 waypoint 없는 경우)
     */
    fun resolveRoute(
        callsign: String,
        waypoints: List<TrackWaypoint>
    ): Pair<String?, String?> {
        if (waypoints.isEmpty()) return null to null

        val sorted = waypoints.sortedBy { it.time }
        val trimmed = callsign.trim()

        // 항공사 IATA 코드 추출
        val airlineIata = extractAirlineIata(trimmed)

        // 1단계: onGround waypoint로 출발/도착 공항 식별
        val groundStart = sorted.takeWhile { it.onGround }.lastOrNull()
            ?: sorted.take(3).firstOrNull { it.onGround }
        val groundEnd = sorted.takeLastWhile { it.onGround }.firstOrNull()
            ?: sorted.takeLast(3).lastOrNull { it.onGround }

        val depFromGround = groundStart?.let { findNearestAirport(it.latitude, it.longitude, 0.5) }
        val arrFromGround = groundEnd?.let {
            // 출발지와 같은 waypoint가 아닌 경우만
            if (groundStart != null && it.time > groundStart.time + 300)
                findNearestAirport(it.latitude, it.longitude, 0.5)
            else null
        }

        // 지상 waypoint로 출발공항 확정된 경우
        if (depFromGround != null) {
            log.info("Departure from ground waypoint: ${depFromGround.iata}")
            val arrival = arrFromGround?.iata
                ?: resolveDestinationFromRouteDB(airlineIata, depFromGround, sorted)
            log.info("Route resolved: ${depFromGround.iata} → $arrival (callsign=$trimmed)")
            return depFromGround.iata to arrival
        }

        // 2단계: 지상 waypoint 없음 → 첫 waypoint 근처 공항 + route DB로 도착지 결정
        // ※ 마지막 waypoint는 현재 비행 위치일 뿐 도착지가 아님 → 도착지는 반드시 route DB에서 결정
        val firstWp = sorted.first()

        // 순항 중인 첫 점은 출발지에 대해 아무것도 알려주지 않는다
        val nearDeparture = firstWp.onGround || (firstWp.altitude ?: Double.MAX_VALUE) < DEPARTURE_MAX_ALTITUDE_M
        if (!nearDeparture) {
            log.info("Route unresolved: first waypoint is airborne at ${firstWp.altitude} m (callsign=$trimmed)")
            return null to null
        }

        // 첫 waypoint 근처 공항 후보들 (넓은 범위)
        val nearFirst = findNearbyAirports(firstWp.latitude, firstWp.longitude, 2.0, 5)

        if (airlineIata != null) {
            // 첫 waypoint 근처 공항 중 route가 있는 것 사용 → 도착지는 route DB + heading으로 결정
            for (dep in nearFirst) {
                val arrival = resolveDestinationFromRouteDB(airlineIata, dep, sorted)
                if (arrival != null) {
                    log.info("Route resolved: ${dep.iata} → $arrival (callsign=$trimmed, airline=$airlineIata)")
                    return dep.iata to arrival
                }
            }
        }

        // 3단계: route DB 매칭 실패 → 출발공항만 반환
        val depAirport = nearFirst.firstOrNull()?.iata
        log.info("Route fallback (no route DB match): dep=$depAirport (callsign=$trimmed)")
        return depAirport to null
    }

    private fun extractAirlineIata(callsign: String): String? {
        if (callsign.length < 3) return null
        val airlineIcao = callsign.take(3).takeIf { it.all { c -> c.isLetter() } }
            ?: callsign.take(2).takeIf { it.all { c -> c.isLetter() } }
            ?: return null
        val iata = data.airlineIataByIcao[airlineIcao]
        if (iata != null) {
            log.info("Airline resolved: $airlineIcao → $iata (${data.airlineNameByIcao[airlineIcao]})")
        } else {
            log.warn("Airline ICAO '$airlineIcao' not found (callsign='$callsign')")
        }
        return iata
    }

    private fun resolveDestinationFromRouteDB(
        airlineIata: String?,
        depAirport: Airport,
        sortedWaypoints: List<TrackWaypoint>
    ): String? {
        if (airlineIata == null) return null

        val destinations = data.destinationsByRoute[RouteKey(airlineIata, depAirport.iata)]
        if (destinations.isNullOrEmpty()) return null

        if (destinations.size == 1) return destinations[0]

        // 여러 후보 → 현재 위치(마지막 waypoint)에서 heading 방향에 있는 공항 선택
        // 예: PUS 위를 지나가면서 ICN 방향(310°)으로 비행 중이면
        //     현재위치→ICN 방위가 heading과 일치 → ICN 선택
        val last = sortedWaypoints.last()
        val heading = last.heading ?: return null

        return destinations
            .mapNotNull { dst ->
                val dstAirport = data.airports[dst] ?: return@mapNotNull null
                val bearingFromCurrent = calcBearing(last.latitude, last.longitude, dstAirport.lat, dstAirport.lng)
                val diff = angleDiff(heading, bearingFromCurrent)
                dst to diff
            }
            .minByOrNull { it.second }
            ?.first
            ?: destinations[0]
    }

    fun findNearestAirport(lat: Double, lng: Double, maxDistSq: Double): Airport? {
        return findNearbyAirports(lat, lng, maxDistSq, 1).firstOrNull()
    }

    private fun findNearbyAirports(lat: Double, lng: Double, maxDistSq: Double, limit: Int): List<Airport> {
        return data.airports.values
            .map { airport ->
                val dlat = airport.lat - lat
                val dlng = airport.lng - lng
                airport to (dlat * dlat + dlng * dlng)
            }
            .filter { it.second < maxDistSq }
            .sortedBy { it.second }
            .take(limit)
            .map { it.first }
    }

    private fun calcBearing(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Double {
        val dLng = Math.toRadians(lng2 - lng1)
        val lat1R = Math.toRadians(lat1)
        val lat2R = Math.toRadians(lat2)
        val y = Math.sin(dLng) * Math.cos(lat2R)
        val x = Math.cos(lat1R) * Math.sin(lat2R) - Math.sin(lat1R) * Math.cos(lat2R) * Math.cos(dLng)
        return (Math.toDegrees(Math.atan2(y, x)) + 360) % 360
    }

    private fun angleDiff(a: Double, b: Double): Double {
        val diff = Math.abs(a - b) % 360
        return if (diff > 180) 360 - diff else diff
    }

    private companion object {
        const val DEPARTURE_MAX_ALTITUDE_M = 3_000.0
    }
}
