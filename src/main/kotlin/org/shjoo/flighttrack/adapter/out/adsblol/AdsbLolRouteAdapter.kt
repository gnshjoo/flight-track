package org.shjoo.flighttrack.adapter.out.adsblol

import io.netty.channel.ChannelOption
import org.shjoo.flighttrack.adapter.out.adsblol.dto.AdsbLolPlaneQuery
import org.shjoo.flighttrack.adapter.out.adsblol.dto.AdsbLolRouteEntry
import org.shjoo.flighttrack.adapter.out.adsblol.dto.AdsbLolRouteRequest
import org.shjoo.flighttrack.domain.model.FlightRoute
import org.shjoo.flighttrack.domain.port.out.FlightRoutePort
import org.slf4j.LoggerFactory
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodyOrNull
import reactor.netty.http.client.HttpClient
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

@Component
class AdsbLolRouteAdapter : FlightRoutePort {

    private val log = LoggerFactory.getLogger(AdsbLolRouteAdapter::class.java)

    private val client: WebClient by lazy {
        val httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
            .responseTimeout(Duration.ofSeconds(15))
        WebClient.builder()
            .baseUrl("https://api.adsb.lol")
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .codecs { it.defaultCodecs().maxInMemorySize(2 * 1024 * 1024) }
            .build()
    }

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

        return try {
            val body = AdsbLolRouteRequest(
                planes = listOf(AdsbLolPlaneQuery(key, lat ?: 0.0, lng ?: 0.0))
            )
            val response = client.post()
                .uri("/api/0/routeset")
                .bodyValue(body)
                .retrieve()
                .awaitBodyOrNull<List<AdsbLolRouteEntry>>()

            val entry = response?.firstOrNull { it.callsign.trim().equals(key, ignoreCase = true) }
                ?: response?.firstOrNull()
            val route = entry?.toFlightRoute()

            if (route != null) {
                positiveCache[key] = now to route
                negativeCache.remove(key)
                log.info("adsb.lol route: $key → ${route.departureIata}-${route.arrivalIata} plausible=${route.plausible}")
            } else {
                negativeCache[key] = now
            }
            evictIfOversized(now)
            route
        } catch (e: Exception) {
            log.warn("adsb.lol routeset failed for $key: ${e.message}")
            positiveCache[key]?.second
        }
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
}
