package org.shjoo.flighttrack.adapter.out.hexdb

import io.netty.channel.ChannelOption
import kotlinx.coroutines.CancellationException
import org.shjoo.flighttrack.adapter.out.hexdb.dto.HexDbAircraftResponse
import org.shjoo.flighttrack.domain.model.AircraftInfo
import org.shjoo.flighttrack.domain.model.Icao24
import org.shjoo.flighttrack.domain.model.UpstreamUnavailableException
import org.shjoo.flighttrack.domain.port.out.AircraftMetadataPort
import org.slf4j.LoggerFactory
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.WebClientResponseException
import org.springframework.web.reactive.function.client.awaitBodyOrNull
import reactor.netty.http.client.HttpClient
import java.time.Clock
import java.time.Duration
import java.util.concurrent.ConcurrentHashMap

@Component
class HexDbMetadataAdapter internal constructor(
    private val client: WebClient,
    private val clock: Clock,
) : AircraftMetadataPort {

    @Autowired
    constructor() : this(defaultClient(), Clock.systemUTC())

    private val log = LoggerFactory.getLogger(HexDbMetadataAdapter::class.java)

    private data class Entry(val at: Long, val info: AircraftInfo?)

    private val cache = ConcurrentHashMap<String, Entry>()

    override suspend fun fetchAircraftMetadata(icao24: Icao24): AircraftInfo? {
        val now = clock.millis()
        val key = icao24.hex.uppercase()

        cache[key]?.let { if ((now - it.at) < CACHE_MS) return it.info }

        return try {
            val info = client.get()
                .uri("/api/v1/aircraft/$key")
                .retrieve()
                .awaitBodyOrNull<HexDbAircraftResponse>()
                ?.toAircraftInfo(key)
            remember(key, Entry(now, info))
            info
        } catch (e: WebClientResponseException.NotFound) {
            log.debug("HexDB has no aircraft {}", key)
            remember(key, Entry(now, null))
            null
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            log.warn("HexDB metadata fetch failed for {}: {}", key, e.message)
            cache[key]?.let { return it.info }
            throw UpstreamUnavailableException("hexdb")
        }
    }

    private fun remember(key: String, entry: Entry) {
        cache[key] = entry
        if (cache.size > 200) {
            val cutoff = entry.at - CACHE_MS * 2
            cache.entries.removeIf { it.value.at < cutoff }
        }
    }

    private fun HexDbAircraftResponse.toAircraftInfo(key: String) = AircraftInfo(
        icao24 = ModeS.ifBlank { key },
        registration = Registration?.ifBlank { null },
        manufacturerName = Manufacturer?.ifBlank { null },
        model = Type?.ifBlank { null },
        operator = RegisteredOwners?.ifBlank { null },
        owner = null,
        built = null,
        categoryDescription = ICAOTypeCode?.ifBlank { null }
    )

    private companion object {
        const val CACHE_MS = 300_000L // 5분 캐시 (메타데이터는 잘 안 변함)

        fun defaultClient(): WebClient = WebClient.builder()
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
}
