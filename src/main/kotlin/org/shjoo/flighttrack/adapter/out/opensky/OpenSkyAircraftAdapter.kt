package org.shjoo.flighttrack.adapter.out.opensky

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.shjoo.flighttrack.adapter.out.openflights.RouteResolver
import org.shjoo.flighttrack.adapter.out.opensky.dto.OpenSkyStatesResponse
import org.shjoo.flighttrack.adapter.out.opensky.dto.OpenSkyTrackResponse
import org.shjoo.flighttrack.config.OpenSkyConfig
import org.shjoo.flighttrack.adapter.out.opensky.dto.HexDbAircraftResponse
import org.shjoo.flighttrack.domain.model.AircraftInfo
import org.shjoo.flighttrack.domain.model.AircraftSnapshot
import org.shjoo.flighttrack.domain.model.AircraftState
import org.shjoo.flighttrack.domain.model.Track
import org.shjoo.flighttrack.domain.model.TrackWaypoint
import org.shjoo.flighttrack.domain.port.out.AircraftMetadataPort
import org.shjoo.flighttrack.domain.port.out.AircraftTrackPort
import org.shjoo.flighttrack.domain.port.out.AircraftTrackingPort
import org.slf4j.LoggerFactory
import io.netty.channel.ChannelOption
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.http.client.reactive.ReactorClientHttpConnector
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodyOrNull
import reactor.netty.http.client.HttpClient
import java.time.Duration

@Component
@EnableConfigurationProperties(OpenSkyConfig::class)
class OpenSkyAircraftAdapter(
    private val openSkyConfig: OpenSkyConfig,
    private val routeResolver: RouteResolver
) : AircraftTrackingPort, AircraftTrackPort, AircraftMetadataPort {

    private val log = LoggerFactory.getLogger(OpenSkyAircraftAdapter::class.java)

    private val openskyClient: WebClient by lazy {
        val httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 30_000)
            .responseTimeout(Duration.ofSeconds(60))

        val builder = WebClient.builder()
            .baseUrl(openSkyConfig.baseUrl)
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .codecs { it.defaultCodecs().maxInMemorySize(10 * 1024 * 1024) }

        if (openSkyConfig.hasCredentials) {
            builder.defaultHeaders { it.setBasicAuth(openSkyConfig.username, openSkyConfig.password) }
            log.info("OpenSky Network: authenticated as ${openSkyConfig.username}")
        } else {
            log.info("OpenSky Network: running without credentials (rate-limited)")
        }

        builder.build()
    }

    @Volatile private var cachedSnapshot: AircraftSnapshot? = null
    @Volatile private var cachedSnapshotTime: Long = 0
    private val SNAPSHOT_CACHE_MS = 300_000L // 5분 캐시 — API 호출 최소화
    private val fetchMutex = Mutex()
    private val MAX_RETRIES = 3
    private val RETRY_DELAY_MS = 3_000L

    @Volatile private var cachedTracks = mutableMapOf<String, Pair<Long, Track>>()
    private val TRACK_CACHE_MS = 300_000L // 5분 캐시

    @Volatile private var cachedMetadata = mutableMapOf<String, Pair<Long, AircraftInfo?>>()
    private val METADATA_CACHE_MS = 300_000L // 5분 캐시 (메타데이터는 잘 안 변함)

    override suspend fun fetchAllAircraft(): AircraftSnapshot {
        val now = System.currentTimeMillis()

        val cached = cachedSnapshot
        if (cached != null && (now - cachedSnapshotTime) < SNAPSHOT_CACHE_MS) {
            return cached
        }

        return fetchMutex.withLock {
            val freshCached = cachedSnapshot
            if (freshCached != null && (System.currentTimeMillis() - cachedSnapshotTime) < SNAPSHOT_CACHE_MS) {
                return@withLock freshCached
            }

            var lastException: Exception? = null
            for (attempt in 1..MAX_RETRIES) {
                try {
                    val response = openskyClient.get()
                        .uri { it.path("/api/states/all").build() }
                        .retrieve()
                        .awaitBodyOrNull<OpenSkyStatesResponse>()

                    val fetchTime = System.currentTimeMillis()

                    if (response?.states == null) {
                        val empty = AircraftSnapshot(time = fetchTime / 1000, aircraft = emptyList())
                        cachedSnapshot = empty
                        cachedSnapshotTime = fetchTime
                        return@withLock empty
                    }

                    val aircraft = response.states
                        .filter { state ->
                            state.size > 11 &&
                            state[5] != null && state[6] != null &&
                            state[8] == false
                        }
                        .map { state ->
                            AircraftState(
                                icao24 = state[0] as? String ?: "",
                                callsign = (state[1] as? String)?.trim() ?: "",
                                originCountry = state[2] as? String ?: "",
                                longitude = (state[5] as? Number)?.toDouble() ?: 0.0,
                                latitude = (state[6] as? Number)?.toDouble() ?: 0.0,
                                altitude = (state[7] as? Number)?.toDouble(),
                                velocity = (state[9] as? Number)?.toDouble(),
                                heading = (state[10] as? Number)?.toDouble(),
                                verticalRate = (state[11] as? Number)?.toDouble()
                            )
                        }

                    val result = AircraftSnapshot(time = response.time, aircraft = aircraft)
                    cachedSnapshot = result
                    cachedSnapshotTime = fetchTime
                    if (attempt > 1) log.info("OpenSky API succeeded on attempt $attempt")
                    return@withLock result
                } catch (e: Exception) {
                    lastException = e
                    log.warn("OpenSky API attempt $attempt/$MAX_RETRIES failed: ${e.message}")
                    if (attempt < MAX_RETRIES) delay(RETRY_DELAY_MS * attempt)
                }
            }
            log.error("OpenSky API failed after $MAX_RETRIES attempts: ${lastException?.message}")
            cachedSnapshot ?: AircraftSnapshot(time = System.currentTimeMillis() / 1000, aircraft = emptyList())
        }
    }

    override suspend fun fetchTrack(icao24: String): Track {
        val now = System.currentTimeMillis()
        val key = icao24.lowercase()

        cachedTracks[key]?.let { (ts, track) ->
            if ((now - ts) < TRACK_CACHE_MS) return track
        }

        return try {
            val response = openskyClient.get()
                .uri { builder ->
                    builder.path("/api/tracks/all")
                        .queryParam("icao24", key)
                        .queryParam("time", 0)
                        .build()
                }
                .retrieve()
                .awaitBodyOrNull<OpenSkyTrackResponse>()

            if (response?.path == null || response.path.isEmpty()) {
                val empty = Track(icao24 = icao24, callsign = "", path = emptyList())
                cachedTracks[key] = now to empty
                return empty
            }

            val waypoints = response.path.mapNotNull { wp ->
                if (wp.size < 6) return@mapNotNull null
                val lat = (wp[1] as? Number)?.toDouble() ?: return@mapNotNull null
                val lng = (wp[2] as? Number)?.toDouble() ?: return@mapNotNull null
                TrackWaypoint(
                    time = (wp[0] as? Number)?.toLong() ?: 0,
                    latitude = lat,
                    longitude = lng,
                    altitude = (wp[3] as? Number)?.toDouble(),
                    heading = (wp[4] as? Number)?.toDouble(),
                    onGround = wp[5] == true
                )
            }

            // Resolve departure/arrival airports via callsign + waypoints + route DB
            val callsign = response.callsign?.trim() ?: ""
            val (depAirport, arrAirport) = routeResolver.resolveRoute(
                callsign = callsign,
                waypoints = waypoints
            )
            log.info("Route resolved for $key (callsign=$callsign): dep=$depAirport, arr=$arrAirport")

            val result = Track(
                icao24 = response.icao24,
                callsign = response.callsign?.trim() ?: "",
                path = waypoints,
                estDepartureAirport = depAirport,
                estArrivalAirport = arrAirport
            )
            cachedTracks[key] = now to result

            if (cachedTracks.size > 100) {
                val cutoff = now - TRACK_CACHE_MS * 10
                cachedTracks.entries.removeIf { it.value.first < cutoff }
            }

            result
        } catch (e: Exception) {
            log.error("OpenSky track fetch failed for $icao24: ${e.message}")
            cachedTracks[key]?.second ?: Track(icao24 = icao24, callsign = "", path = emptyList())
        }
    }

    private val hexDbClient: WebClient by lazy {
        WebClient.builder()
            .baseUrl("https://hexdb.io")
            .clientConnector(ReactorClientHttpConnector(
                HttpClient.create()
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, 10_000)
                    .responseTimeout(Duration.ofSeconds(10))
            ))
            .build()
    }

    override suspend fun fetchAircraftMetadata(icao24: String): AircraftInfo? {
        val now = System.currentTimeMillis()
        val key = icao24.uppercase()

        cachedMetadata[key]?.let { (ts, info) ->
            if ((now - ts) < METADATA_CACHE_MS) return info
        }

        return try {
            val response = hexDbClient.get()
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

            cachedMetadata[key] = now to info

            if (cachedMetadata.size > 200) {
                val cutoff = now - METADATA_CACHE_MS * 2
                cachedMetadata.entries.removeIf { it.value.first < cutoff }
            }

            info
        } catch (e: Exception) {
            log.error("HexDB metadata fetch failed for $icao24: ${e.message}")
            cachedMetadata[key]?.second
        }
    }

}
