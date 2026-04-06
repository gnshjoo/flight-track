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

    // airport IATA → (lat, lng)
    private val airportCoords = mapOf(
        "BKK" to (13.6900 to 100.7501), "MNL" to (14.5995 to 120.9842),
        "SGN" to (10.8231 to 106.6297), "NRT" to (35.7720 to 140.3929),
        "KIX" to (34.4347 to 135.2440), "TPE" to (25.0797 to 121.2342),
        "HKG" to (22.3080 to 113.9185), "SIN" to (1.3644 to 103.9915),
        "KUL" to (2.7456 to 101.7099), "IST" to (41.2753 to 28.7519),
        "LHR" to (51.4700 to -0.4543), "CDG" to (49.0097 to 2.5479),
        "FCO" to (41.8003 to 12.2389), "BCN" to (41.2974 to 2.0833),
        "DXB" to (25.2532 to 55.3657), "SYD" to (-33.9399 to 151.1753),
        "HAN" to (21.2187 to 105.8044), "DPS" to (-8.7467 to 115.1670),
        "DAD" to (16.0439 to 108.1989), "FUK" to (33.5902 to 130.4017),
        "CTS" to (42.7752 to 141.6922), "PEK" to (40.0799 to 116.6031),
        "PVG" to (31.1443 to 121.8083), "BOM" to (19.0896 to 72.8656),
        "DEL" to (28.5562 to 77.1000), "VVO" to (43.3950 to 132.1444),
        "GUM" to (13.4835 to 144.7961), "PNH" to (11.5464 to 104.8440),
        "CEB" to (10.3075 to 123.9794), "CNX" to (18.7669 to 98.9625),
        "ICN" to (37.4602 to 126.4407), "JFK" to (40.6413 to -73.7781),
        "LAX" to (33.9416 to -118.4085), "ORD" to (41.9742 to -87.9073),
        "ATL" to (33.6407 to -84.4277), "DFW" to (32.8998 to -97.0403),
        "FRA" to (50.0379 to 8.5622), "AMS" to (52.3105 to 4.7683),
        "MAD" to (40.4983 to -3.5676), "MUC" to (48.3537 to 11.7750),
        "DOH" to (25.2731 to 51.6081), "HND" to (35.5494 to 139.7798),
        "SFO" to (37.6213 to -122.3790), "MIA" to (25.7959 to -80.2870),
        "YYZ" to (43.6777 to -79.6248), "MEX" to (19.4361 to -99.0719),
        "GRU" to (-23.4356 to -46.4731), "EZE" to (-34.8222 to -58.5358),
        "JNB" to (-26.1392 to 28.2460), "CAI" to (30.1219 to 31.4056),
        "NBO" to (-1.3192 to 36.9278), "ADD" to (8.9779 to 38.7993),
        "GMP" to (37.5583 to 126.7906), "ITM" to (34.7855 to 135.4380),
        "OKA" to (26.1958 to 127.6459), "CJU" to (33.5114 to 126.4929),
        "PUS" to (35.1795 to 128.9382), "TAE" to (35.8941 to 128.6559),
        "CJJ" to (36.7166 to 127.4991), "KWJ" to (35.1264 to 126.8089),
        "RSU" to (34.8424 to 127.6161), "USN" to (35.5935 to 129.3519),
        "WJU" to (37.4383 to 127.9601), "MWX" to (34.9914 to 126.3828),
        "HIN" to (35.0886 to 128.0703), "YNY" to (38.0613 to 128.6692)
    )

    @PostConstruct
    fun init() {
        try {
            loadAirlines()
            loadRoutes()
            loadAirportNames()
            log.info("RouteResolver loaded: ${airlineIcaoToIata.size} airlines, ${routeMap.size} route pairs, ${airportNames.size} airports")
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
     * 공항 이름 데이터 (OpenFlights airports.dat)
     */
    private fun loadAirportNames() {
        val url = "https://raw.githubusercontent.com/jpatokal/openflights/master/data/airports.dat"
        val text = URI(url).toURL().readText()
        for (line in text.lines()) {
            val cols = parseCsvLine(line)
            if (cols.size < 5) continue
            val name = cols[1].trim().removeSurrounding("\"")
            val iata = cols[4].trim().removeSurrounding("\"")
            if (iata.isNotBlank() && iata != "\\N" && iata.length == 3) {
                airportNames[iata] = name
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
