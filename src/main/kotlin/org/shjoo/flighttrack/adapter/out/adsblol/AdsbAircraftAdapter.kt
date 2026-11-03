package org.shjoo.flighttrack.adapter.out.adsblol

import io.netty.channel.ChannelOption
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import org.shjoo.flighttrack.adapter.out.adsblol.dto.AdsbLolV2Response
import org.shjoo.flighttrack.adapter.out.adsblol.dto.AdsbTraceResponse
import org.shjoo.flighttrack.adapter.out.openflights.RouteResolver
import org.shjoo.flighttrack.domain.model.AircraftSnapshot
import org.shjoo.flighttrack.domain.model.BoundingBox
import org.shjoo.flighttrack.domain.model.Icao24
import org.shjoo.flighttrack.domain.model.Track
import org.shjoo.flighttrack.domain.model.TrackWaypoint
import org.shjoo.flighttrack.domain.model.UpstreamUnavailableException
import org.shjoo.flighttrack.domain.port.out.AircraftTrackPort
import org.shjoo.flighttrack.domain.port.out.AircraftTrackingPort
import org.shjoo.flighttrack.domain.port.out.FlightRoutePort
import org.slf4j.LoggerFactory
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodyOrNull
import reactor.netty.http.client.HttpClient
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

/**
 * ADS-B 오픈 API(readsb v2 포맷) 기반 실시간 추적 어댑터.
 *
 * 주 소스는 adsb.lol, 실패 시 동일 포맷의 adsb.fi 로 폴백한다.
 * OpenSky 는 클라우드(AWS 등) IP 대역에서의 접속을 네트워크 레벨에서 차단하여
 * Vercel 배포 환경에서 사용할 수 없다. 위치 조회는 point+radius(최대 250nm)
 * 방식이므로 bbox 를 외접원으로 변환해 조회한다.
 */
@Component
class AdsbAircraftAdapter(
    private val routeResolver: RouteResolver,
    private val flightRoutePort: FlightRoutePort,
) : AircraftTrackingPort, AircraftTrackPort {

    private val log = LoggerFactory.getLogger(AdsbAircraftAdapter::class.java)

    private fun buildClient(baseUrl: String): WebClient {
        val httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
            .responseTimeout(Duration.ofSeconds(15))
            .compress(true)
        return WebClient.builder()
            .baseUrl(baseUrl)
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .codecs { it.defaultCodecs().maxInMemorySize(10 * 1024 * 1024) }
            .build()
    }

    /** 폴백 순서대로 나열된 데이터 소스. 경로 형식이 소스마다 달라 함수로 만든다. */
    private inner class V2Source(
        val name: String,
        baseUrl: String,
        val pointPath: (Double, Double, Int) -> String,
        val callsignPath: (String) -> String,
        val icaoPath: (String) -> String,
    ) {
        val client: WebClient by lazy { buildClient(baseUrl) }
    }

    private val sources = listOf(
        V2Source(
            name = "adsb.lol",
            baseUrl = "https://api.adsb.lol",
            pointPath = { lat, lon, r -> "/v2/point/$lat/$lon/$r" },
            callsignPath = { cs -> "/v2/callsign/$cs" },
            icaoPath = { hex -> "/v2/icao/$hex" },
        ),
        V2Source(
            name = "adsb.fi",
            baseUrl = "https://opendata.adsb.fi",
            pointPath = { lat, lon, r -> "/api/v2/lat/$lat/lon/$lon/dist/$r" },
            callsignPath = { cs -> "/api/v2/callsign/$cs" },
            icaoPath = { hex -> "/api/v2/icao/$hex" },
        ),
    )

    /**
     * tar1090 globe trace 소스 (과거 경로). REST API 와 달리 Referer 헤더가 필요하다.
     * airplanes.live 는 REST API 는 승인제지만 globe trace 는 공개되어 있다.
     */
    private inner class TraceSource(val name: String, baseUrl: String, val referer: String) {
        val client: WebClient by lazy { buildClient(baseUrl) }
    }

    private val traceSources = listOf(
        TraceSource("airplanes.live", "https://globe.airplanes.live", "https://globe.airplanes.live/"),
        TraceSource("adsb.lol", "https://adsb.lol", "https://globe.adsb.lol/"),
    )

    // bbox 미지정 시 기본 조회 지역: 한반도 중심, 최대 반경
    private val DEFAULT_LAT = 36.5
    private val DEFAULT_LON = 127.8

    private val snapshotCache = ConcurrentHashMap<String, Pair<Long, AircraftSnapshot>>()
    private val SNAPSHOT_CACHE_MS = 30_000L
    private val trackCache = ConcurrentHashMap<String, Pair<Long, Track>>()
    private val TRACK_CACHE_MS = 30_000L

    override suspend fun fetchAircraft(box: BoundingBox?): AircraftSnapshot {
        val (lat, lon, radiusNm) = box?.toCircle() ?: Circle(DEFAULT_LAT, DEFAULT_LON, MAX_RADIUS_NM)
        return fetchPoint(lat, lon, radiusNm)
    }

    override suspend fun fetchAircraftByCallsign(callsign: String): AircraftSnapshot {
        val key = callsign.trim().uppercase()

        val exact = getWithFallback { it.callsignPath(key) }
        val exactStates = exact?.let { r -> r.states.orEmpty().mapNotNull { it.toState(r.nowSeconds) } }.orEmpty()
        if (exactStates.isNotEmpty()) {
            return AircraftSnapshot(time = exact!!.epochSeconds, aircraft = exactStates)
        }

        // 정확 매칭이 없으면(예: "KE" 같은 항공사 prefix 검색) 기본 지역 스냅샷에서 부분 매칭
        val regional = fetchPoint(DEFAULT_LAT, DEFAULT_LON, MAX_RADIUS_NM)
        val filtered = regional.aircraft.filter { it.callsign.contains(key, ignoreCase = true) }
        return AircraftSnapshot(time = regional.time, aircraft = filtered)
    }

    override suspend fun fetchTrack(icao24: Icao24): Track {
        val now = System.currentTimeMillis()
        val key = icao24.hex

        trackCache[key]?.let { (ts, track) ->
            if ((now - ts) < TRACK_CACHE_MS) return track
        }

        // 콜사인/현재 위치는 REST API 로, 과거 경로는 globe trace 로 가져온다
        val (response, parsed) = coroutineScope {
            val rest = async { getWithFallback { it.icaoPath(key) } }
            val trace = async { fetchTrace(key) }
            rest.await() to trace.await()
        }
        val ac = response?.states?.firstOrNull { it.lat != null && it.lon != null }

        val waypoints = when {
            parsed != null -> parsed.waypoints
            ac != null -> listOf(
                TrackWaypoint(
                    time = now / 1000,
                    latitude = ac.lat!!,
                    longitude = ac.lon!!,
                    altitude = (ac.altBaro as? Number)?.toDouble()?.times(FT_TO_M),
                    heading = ac.track,
                    onGround = ac.altBaro == "ground"
                )
            )
            else -> emptyList()
        }
        if (response == null && parsed == null) throw UpstreamUnavailableException("adsb")
        if (waypoints.isEmpty()) {
            val empty = Track(icao24 = key, callsign = "", path = emptyList())
            trackCache[key] = now to empty
            return empty
        }

        // 출발/도착 공항 추정은 기존과 동일하게 노선 DB → 휴리스틱 순서로 시도.
        val last = waypoints.last()
        val callsign = ac?.flight?.trim()?.ifBlank { null } ?: parsed?.callsign ?: ""

        val adsbLolRoute = if (callsign.isNotBlank()) {
            flightRoutePort.resolveRoute(callsign, last.latitude, last.longitude)
        } else null

        val depAirport: String?
        val arrAirport: String?
        if (adsbLolRoute != null) {
            depAirport = adsbLolRoute.departureIata
            arrAirport = adsbLolRoute.arrivalIata
            log.info("Route from callsign DB for $key (callsign=$callsign): $depAirport → $arrAirport plausible=${adsbLolRoute.plausible}")
        } else {
            val (dep, arr) = routeResolver.resolveRoute(callsign, waypoints)
            depAirport = dep
            arrAirport = arr
            log.info("Route from heuristic for $key (callsign=$callsign): $depAirport → $arrAirport")
        }

        val result = Track(
            icao24 = ac?.hex?.ifBlank { null } ?: key,
            callsign = callsign,
            path = waypoints,
            estDepartureAirport = depAirport,
            estArrivalAirport = arrAirport
        )
        trackCache[key] = now to result

        if (trackCache.size > 100) {
            val cutoff = now - TRACK_CACHE_MS * 10
            trackCache.entries.removeIf { it.value.first < cutoff }
        }

        return result
    }

    private suspend fun fetchTrace(hex: String): ParsedTrace? {
        val dir = hex.takeLast(2)
        for (source in traceSources) {
            try {
                val response = source.client.get()
                    .uri("/data/traces/$dir/trace_full_$hex.json")
                    .header("Referer", source.referer)
                    .retrieve()
                    .awaitBodyOrNull<AdsbTraceResponse>()

                val parsed = response?.let { parseTrace(it, System.currentTimeMillis() / 1000) }
                if (parsed != null) {
                    if (source != traceSources.first()) log.info("Trace served by fallback ${source.name} for $hex")
                    return parsed
                }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                log.warn("${source.name} trace fetch failed for $hex: ${e.message}")
            }
        }
        return null
    }

    private suspend fun fetchPoint(lat: Double, lon: Double, radiusNm: Int): AircraftSnapshot {
        val now = System.currentTimeMillis()
        val cacheKey = "%.2f/%.2f/%d".format(lat, lon, radiusNm)

        snapshotCache[cacheKey]?.let { (ts, snapshot) ->
            if ((now - ts) < SNAPSHOT_CACHE_MS) return snapshot
        }

        val response = getWithFallback { it.pointPath(lat, lon, radiusNm) }
            ?: return snapshotCache[cacheKey]?.second
                ?: throw UpstreamUnavailableException("adsb")

        val aircraft = response.states.orEmpty().mapNotNull { it.toState(response.nowSeconds) }
        val result = AircraftSnapshot(
            time = if (response.epochSeconds > 0) response.epochSeconds else now / 1000,
            aircraft = aircraft
        )
        snapshotCache[cacheKey] = now to result

        if (snapshotCache.size > 50) {
            val cutoff = now - SNAPSHOT_CACHE_MS * 10
            snapshotCache.entries.removeIf { it.value.first < cutoff }
        }

        return result
    }

    /**
     * 소스를 순서대로 시도해 첫 정상 응답을 반환. 항공기 0대는 정상 응답이므로
     * 폴백하지 않고, 요청 실패/빈 본문일 때만 다음 소스로 넘어간다.
     */
    private suspend fun getWithFallback(path: (V2Source) -> String): AdsbLolV2Response? {
        for (source in sources) {
            val uri = path(source)
            try {
                val response = source.client.get()
                    .uri(uri)
                    .retrieve()
                    .awaitBodyOrNull<AdsbLolV2Response>()
                if (response?.states != null) {
                    if (source != sources.first()) log.info("Served by fallback source ${source.name}: $uri")
                    return response
                }
                log.warn("${source.name} returned empty body: $uri")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                log.warn("${source.name} request failed ($uri): ${e.message}")
            }
        }
        log.error("All ADS-B sources failed for request")
        return null
    }
}
