package org.shjoo.flighttrack.adapter.out.openflights

import jakarta.annotation.PostConstruct
import org.shjoo.flighttrack.domain.model.Airport
import org.shjoo.flighttrack.domain.model.TrackWaypoint
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.net.URI

@Component
class RouteResolver {

    private val log = LoggerFactory.getLogger(RouteResolver::class.java)

    // airline ICAO 3-letter → IATA 2-letter (e.g., "KAL" → "KE")
    private val airlineIcaoToIata = mutableMapOf<String, String>()
    // airline ICAO 3-letter → airline name
    private val airlineIcaoToName = mutableMapOf<String, String>()

    // (airlineIata, sourceAirportIata) → list of destination IATA codes
    private val routeMap = mutableMapOf<String, MutableList<String>>()

    // airport IATA → airport name
    private val airportNames = mutableMapOf<String, String>()

    // airport IATA → (lat, lng), loaded from OpenFlights airports.dat
    private val airportCoords = mutableMapOf<String, Pair<Double, Double>>()

    @PostConstruct
    fun init() {
        try {
            loadAirlines()
            loadRoutes()
            loadAirports()
            log.info("RouteResolver loaded: ${airlineIcaoToIata.size} airlines, ${routeMap.size} route pairs, ${airportNames.size} airport names, ${airportCoords.size} airport coords")
        } catch (e: Exception) {
            log.error("Failed to load data: ${e.message}")
        }
    }

    /**
     * 항공사 ICAO→IATA 매핑 (benct/iata-utils, 1100+ airlines)
     */
    private fun loadAirlines() {
        val url = "https://raw.githubusercontent.com/benct/iata-utils/master/generated/iata_airlines.csv"
        val text = URI(url).toURL().readText()
        for (line in text.lines()) {
            if (line.startsWith("iata_code") || line.isBlank()) continue
            val cols = line.split("^")
            if (cols.size < 3) continue
            val iata = cols[0].trim()
            val icao = cols[1].trim()
            val name = cols[2].trim()
            if (iata.isNotBlank() && icao.isNotBlank()) {
                airlineIcaoToIata[icao] = iata
                airlineIcaoToName[icao] = name
            }
        }
    }

    /**
     * 노선 데이터 (OpenFlights routes.dat)
     */
    private fun loadRoutes() {
        val url = "https://raw.githubusercontent.com/jpatokal/openflights/master/data/routes.dat"
        val text = URI(url).toURL().readText()
        for (line in text.lines()) {
            val cols = parseCsvLine(line)
            if (cols.size < 5) continue
            val airline = cols[0].trim()
            val src = cols[2].trim()
            val dst = cols[4].trim()
            if (airline.isNotBlank() && src.isNotBlank() && dst.isNotBlank()
                && airline != "\\N" && src != "\\N" && dst != "\\N") {
                val key = "$airline|$src"
                routeMap.getOrPut(key) { mutableListOf() }.add(dst)
            }
        }
    }

    /**
     * 공항 이름 + 좌표 데이터 (OpenFlights airports.dat)
     * cols: 0=id, 1=name, 2=city, 3=country, 4=iata, 5=icao, 6=lat, 7=lng, ...
     */
    private fun loadAirports() {
        val url = "https://raw.githubusercontent.com/jpatokal/openflights/master/data/airports.dat"
        val text = URI(url).toURL().readText()
        for (line in text.lines()) {
            val cols = parseCsvLine(line)
            if (cols.size < 8) continue
            val name = cols[1].trim().removeSurrounding("\"")
            val iata = cols[4].trim().removeSurrounding("\"")
            if (iata.isBlank() || iata == "\\N" || iata.length != 3) continue
            airportNames[iata] = name
            val lat = cols[6].trim().removeSurrounding("\"").toDoubleOrNull()
            val lng = cols[7].trim().removeSurrounding("\"").toDoubleOrNull()
            if (lat != null && lng != null) {
                airportCoords[iata] = lat to lng
            }
        }
    }

    fun getAirport(code: String): Airport? {
        val iata = code.uppercase()
        val coords = airportCoords[iata] ?: return null
        val name = airportNames[iata] ?: iata
        return Airport(iata = iata, name = name, lat = coords.first, lng = coords.second)
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        var current = StringBuilder()
        var inQuotes = false
        for (ch in line) {
            when {
                ch == '"' -> inQuotes = !inQuotes
                ch == ',' && !inQuotes -> { result.add(current.toString()); current = StringBuilder() }
                else -> current.append(ch)
            }
        }
        result.add(current.toString())
        return result
    }

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
            log.info("Departure from ground waypoint: $depFromGround")
            val arrival = arrFromGround
                ?: resolveDestinationFromRouteDB(airlineIata, depFromGround, sorted)
            log.info("Route resolved: $depFromGround → $arrival (callsign=$trimmed)")
            return depFromGround to arrival
        }

        // 2단계: 지상 waypoint 없음 → 첫 waypoint 근처 공항 + route DB로 도착지 결정
        // ※ 마지막 waypoint는 현재 비행 위치일 뿐 도착지가 아님 → 도착지는 반드시 route DB에서 결정
        val firstWp = sorted.first()

        // 첫 waypoint 근처 공항 후보들 (넓은 범위)
        val nearFirst = findNearbyAirports(firstWp.latitude, firstWp.longitude, 2.0, 5)

        if (airlineIata != null) {
            // 첫 waypoint 근처 공항 중 route가 있는 것 사용 → 도착지는 route DB + heading으로 결정
            for (dep in nearFirst) {
                val arrival = resolveDestinationFromRouteDB(airlineIata, dep, sorted)
                if (arrival != null) {
                    log.info("Route resolved: $dep → $arrival (callsign=$trimmed, airline=$airlineIata)")
                    return dep to arrival
                }
            }
        }

        // 3단계: route DB 매칭 실패 → 출발공항만 반환
        val depAirport = nearFirst.firstOrNull()
        log.info("Route fallback (no route DB match): dep=$depAirport (callsign=$trimmed)")
        return depAirport to null
    }

    private fun extractAirlineIata(callsign: String): String? {
        if (callsign.length < 3) return null
        val airlineIcao = callsign.take(3).takeIf { it.all { c -> c.isLetter() } }
            ?: callsign.take(2).takeIf { it.all { c -> c.isLetter() } }
            ?: return null
        val iata = airlineIcaoToIata[airlineIcao]
        if (iata != null) {
            log.info("Airline resolved: $airlineIcao → $iata (${airlineIcaoToName[airlineIcao]})")
        } else {
            log.warn("Airline ICAO '$airlineIcao' not found (callsign='$callsign')")
        }
        return iata
    }

    private fun resolveDestinationFromRouteDB(
        airlineIata: String?,
        depAirport: String,
        sortedWaypoints: List<TrackWaypoint>
    ): String? {
        if (airlineIata == null) return null

        val key = "$airlineIata|$depAirport"
        val destinations = routeMap[key]
        if (destinations.isNullOrEmpty()) return null

        if (destinations.size == 1) return destinations[0]

        // 여러 후보 → 현재 위치(마지막 waypoint)에서 heading 방향에 있는 공항 선택
        // 예: PUS 위를 지나가면서 ICN 방향(310°)으로 비행 중이면
        //     현재위치→ICN 방위가 heading과 일치 → ICN 선택
        val last = sortedWaypoints.last()
        val heading = last.heading ?: return destinations[0]

        return destinations
            .mapNotNull { dst ->
                val dstCoords = airportCoords[dst] ?: return@mapNotNull null
                val bearingFromCurrent = calcBearing(last.latitude, last.longitude, dstCoords.first, dstCoords.second)
                val diff = angleDiff(heading, bearingFromCurrent)
                dst to diff
            }
            .minByOrNull { it.second }
            ?.first
            ?: destinations[0]
    }

    fun findNearestAirport(lat: Double, lng: Double, maxDistSq: Double): String? {
        return findNearbyAirports(lat, lng, maxDistSq, 1).firstOrNull()
    }

    private fun findNearbyAirports(lat: Double, lng: Double, maxDistSq: Double, limit: Int): List<String> {
        return airportCoords.entries
            .map { (iata, coords) ->
                val dlat = coords.first - lat
                val dlng = coords.second - lng
                iata to (dlat * dlat + dlng * dlng)
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
}
