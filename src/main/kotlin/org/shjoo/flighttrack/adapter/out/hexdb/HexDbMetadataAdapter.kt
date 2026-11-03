package org.shjoo.flighttrack.adapter.out.hexdb

import io.netty.channel.ChannelOption
import org.shjoo.flighttrack.adapter.out.hexdb.dto.HexDbAircraftResponse
import org.shjoo.flighttrack.domain.model.AircraftInfo
import org.shjoo.flighttrack.domain.model.Icao24
import org.shjoo.flighttrack.domain.port.out.AircraftMetadataPort
import org.slf4j.LoggerFactory
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodyOrNull
import reactor.netty.http.client.HttpClient
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

@Component
class HexDbMetadataAdapter : AircraftMetadataPort {

    private val log = LoggerFactory.getLogger(HexDbMetadataAdapter::class.java)

    private val client: WebClient by lazy {
        WebClient.builder()
            .baseUrl("https://hexdb.io")
            .clientConnector(
                ReactorClientHttpConnector(
                    HttpClient.create()
                        .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
                        .responseTimeout(Duration.ofSeconds(10))
                )
            )
            .build()
    }

    // icao24 → (timestamp, info). 조회 실패(null) 결과도 캐시하기 위해 Pair 로 감싼다.
    private val cache = ConcurrentHashMap<String, Pair<Long, AircraftInfo?>>()
    private val CACHE_MS = 300_000L // 5분 캐시 (메타데이터는 잘 안 변함)

    override suspend fun fetchAircraftMetadata(icao24: Icao24): AircraftInfo? {
        val now = System.currentTimeMillis()
        val key = icao24.hex.uppercase()

        cache[key]?.let { (ts, info) ->
            if ((now - ts) < CACHE_MS) return info
        }

        return try {
            val response = client.get()
                .uri("/api/v1/aircraft/$key")
                .retrieve()
                .awaitBodyOrNull<HexDbAircraftResponse>()

            val info = response?.let {
                AircraftInfo(
                    icao24 = it.ModeS.ifBlank { key },
                    registration = it.Registration?.ifBlank { null },
                    manufacturerName = it.Manufacturer?.ifBlank { null },
                    model = it.Type?.ifBlank { null },
                    operator = it.RegisteredOwners?.ifBlank { null },
                    owner = null,
                    built = null,
                    categoryDescription = it.ICAOTypeCode?.ifBlank { null }
                )
            }

            cache[key] = now to info

            if (cache.size > 200) {
                val cutoff = now - CACHE_MS * 2
                cache.entries.removeIf { it.value.first < cutoff }
            }

            info
        } catch (e: Exception) {
            log.error("HexDB metadata fetch failed for $key: ${e.message}")
            cache[key]?.second
        }
    }
}
