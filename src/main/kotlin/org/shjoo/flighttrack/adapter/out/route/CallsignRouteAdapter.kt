package org.shjoo.flighttrack.adapter.out.route

import io.netty.channel.ChannelOption
import kotlinx.coroutines.CancellationException
import org.shjoo.flighttrack.adapter.out.route.dto.AdsbLolPlaneQuery
import org.shjoo.flighttrack.adapter.out.route.dto.AdsbLolRouteEntry
import org.shjoo.flighttrack.adapter.out.route.dto.AdsbLolRouteRequest
import org.shjoo.flighttrack.adapter.out.route.dto.AdsbdbResponse
import org.shjoo.flighttrack.domain.model.FlightRoute
import org.shjoo.flighttrack.domain.port.out.FlightRoutePort
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.awaitBodyOrNull
import reactor.netty.http.client.HttpClient
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

@Component
class CallsignRouteAdapter internal constructor(
    private val routesetClient: WebClient,
    private val adsbdbClient: WebClient,
) : FlightRoutePort {

    @Autowired
    constructor() : this(
        defaultClient("https://api.adsb.lol", 2 * 1024 * 1024),
        defaultClient("https://api.adsbdb.com", 1024 * 1024),
    )

    private val log = LoggerFactory.getLogger(CallsignRouteAdapter::class.java)

    // callsign → (timestamp, route). 캐시 miss 결과는 별도 NEGATIVE set 으로 관리하여
    // null 값 저장이 불가능한 ConcurrentHashMap 제약 회피.
    private val positiveCache = ConcurrentHashMap<String, Pair<Long, FlightRoute>>()
    private val negativeCache = ConcurrentHashMap<String, Long>()

    private val POSITIVE_TTL_MS = 2 * 60 * 60 * 1000L   // 2h — Shadowbroker 동일
    private val NEGATIVE_TTL_MS = 15 * 60 * 1000L       // 15m — 없는 편명 재시도 간격
    private val MAX_CACHE_SIZE = 5000

    override suspend fun resolveRoute(
        callsign: String,
        lat: Double?,
        lng: Double?
    ): FlightRoute? {
        val key = callsign.trim().uppercase()
        if (key.isEmpty()) return null

        val now = System.currentTimeMillis()

        positiveCache[key]?.let { (ts, route) ->
            if ((now - ts) < POSITIVE_TTL_MS) return route
        }
        negativeCache[key]?.let { ts ->
            if ((now - ts) < NEGATIVE_TTL_MS) return null
        }

        var failed = false
        suspend fun attempt(source: String, fetch: suspend () -> FlightRoute?): FlightRoute? =
            try {
                fetch()?.also { log.info("$source route: $key → ${it.departureIata}-${it.arrivalIata} plausible=${it.plausible}") }
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                log.warn("$source route lookup failed for $key: ${e.message}")
                failed = true
                null
            }

        val route = attempt("adsb.lol") { fetchRouteset(key, lat, lng) }
            ?: attempt("adsbdb") { fetchAdsbdb(key) }

        when {
            route != null -> {
                positiveCache[key] = now to route
                negativeCache.remove(key)
            }
            // 한쪽이 실패했으면 '노선 없음'이 확정이 아니므로 negative 캐시하지 않고 직전 값을 쓴다.
            failed -> return positiveCache[key]?.second
            else -> {
                log.debug("no callsign route for {} in adsb.lol or adsbdb", key)
                negativeCache[key] = now
            }
        }
        evictIfOversized(now)
        return route
    }

    private suspend fun fetchRouteset(key: String, lat: Double?, lng: Double?): FlightRoute? {
        val body = AdsbLolRouteRequest(
            planes = listOf(AdsbLolPlaneQuery(key, lat ?: 0.0, lng ?: 0.0))
        )
        val response = routesetClient.post()
            .uri("/api/0/routeset")
            .bodyValue(body)
            .retrieve()
            .awaitBodyOrNull<List<AdsbLolRouteEntry>>()

        val entry = response?.firstOrNull { it.callsign.trim().equals(key, ignoreCase = true) }
            ?: response?.firstOrNull()
        return entry?.toFlightRoute()
    }

    private suspend fun fetchAdsbdb(key: String): FlightRoute? {
        val route = try {
            adsbdbClient.get()
                .uri("/v0/callsign/{callsign}", key)
                .retrieve()
                .awaitBodyOrNull<AdsbdbResponse>()
                ?.response?.flightroute
        } catch (e: WebClientResponseException.NotFound) {
            null
        } ?: return null
        val dep = route.origin?.iataCode.orEmpty()
        val arr = route.destination?.iataCode.orEmpty()
        if (dep.isBlank() || arr.isBlank()) return null
        return FlightRoute(departureIata = dep, arrivalIata = arr, plausible = true)
    }

    private fun AdsbLolRouteEntry.toFlightRoute(): FlightRoute? {
        val airports = airports ?: return null
        if (airports.size < 2) return null
        val dep = airports.first()
        val arr = airports.last()
        if (dep.iata.isBlank() || arr.iata.isBlank()) return null
        return FlightRoute(
            departureIata = dep.iata,
            arrivalIata = arr.iata,
            plausible = plausible,
        )
    }

    private fun evictIfOversized(now: Long) {
        if (positiveCache.size > MAX_CACHE_SIZE) {
            val cutoff = now - POSITIVE_TTL_MS
            positiveCache.entries.removeIf { it.value.first < cutoff }
        }
        if (negativeCache.size > MAX_CACHE_SIZE) {
            val cutoff = now - NEGATIVE_TTL_MS
            negativeCache.entries.removeIf { it.value < cutoff }
        }
    }

    private companion object {
        fun defaultClient(baseUrl: String, maxInMemorySize: Int): WebClient = WebClient.builder()
            .baseUrl(baseUrl)
            .clientConnector(
                ReactorClientHttpConnector(
                    HttpClient.create()
                        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
                        .responseTimeout(Duration.ofSeconds(15))
                )
            )
            .codecs { it.defaultCodecs().maxInMemorySize(maxInMemorySize) }
            .build()
    }
}
