package org.shjoo.flighttrack.controller

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.slf4j.LoggerFactory
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodyOrNull

@RestController
@RequestMapping("/api/tracking")
class TrackingController {
    private val log = LoggerFactory.getLogger(TrackingController::class.java)

    private val openskyClient = WebClient.builder()
        .baseUrl("https://opensky-network.org")
        .codecs { it.defaultCodecs().maxInMemorySize(10 * 1024 * 1024) }
        .build()

    // --- In-memory cache to avoid 429 rate limits ---
    // OpenSky free tier: ~10 req/min for anonymous, ~1 req/5s for authenticated
    @Volatile private var cachedAircraft: AircraftResponse? = null
    @Volatile private var cachedAircraftTime: Long = 0
    private val AIRCRAFT_CACHE_MS = 30_000L // 30 seconds
    private val fetchMutex = Mutex() // prevent concurrent API calls

    @Volatile private var cachedTracks = mutableMapOf<String, Pair<Long, TrackResponse>>()
    private val TRACK_CACHE_MS = 30_000L // 30 seconds

    @GetMapping("/aircraft")
    suspend fun getAircraft(
        @RequestParam(required = false) lamin: Double?,
        @RequestParam(required = false) lomin: Double?,
        @RequestParam(required = false) lamax: Double?,
        @RequestParam(required = false) lomax: Double?
    ): AircraftResponse {
        val now = System.currentTimeMillis()

        // Return cached global data, filtered by bounds on the server side
        val cached = cachedAircraft
        if (cached != null && (now - cachedAircraftTime) < AIRCRAFT_CACHE_MS) {
            return filterByBounds(cached, lamin, lomin, lamax, lomax)
        }

        // Use mutex to prevent concurrent API calls (both hit OpenSky → both get 429)
        return fetchMutex.withLock {
            // Re-check cache inside lock (another request may have filled it)
            val freshCached = cachedAircraft
            if (freshCached != null && (System.currentTimeMillis() - cachedAircraftTime) < AIRCRAFT_CACHE_MS) {
                return@withLock filterByBounds(freshCached, lamin, lomin, lamax, lomax)
            }

            try {
                val response = openskyClient.get()
                    .uri { it.path("/api/states/all").build() }
                    .retrieve()
                    .awaitBodyOrNull<OpenSkyResponse>()

                val fetchTime = System.currentTimeMillis()

                if (response?.states == null) {
                    val empty = AircraftResponse(time = fetchTime / 1000, aircraft = emptyList())
                    cachedAircraft = empty
                    cachedAircraftTime = fetchTime
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

                val result = AircraftResponse(time = response.time, aircraft = aircraft)
                cachedAircraft = result
                cachedAircraftTime = fetchTime
                filterByBounds(result, lamin, lomin, lamax, lomax)
            } catch (e: Exception) {
                log.error("OpenSky API failed: ${e.message}")
                cachedAircraft?.let { return@withLock filterByBounds(it, lamin, lomin, lamax, lomax) }
                AircraftResponse(time = System.currentTimeMillis() / 1000, aircraft = emptyList())
            }
        }
    }

    private fun filterByBounds(
        data: AircraftResponse,
        lamin: Double?, lomin: Double?, lamax: Double?, lomax: Double?
    ): AircraftResponse {
        if (lamin == null || lomin == null || lamax == null || lomax == null) return data
        val filtered = data.aircraft.filter { ac ->
            ac.latitude in lamin..lamax && ac.longitude in lomin..lomax
        }
        return AircraftResponse(time = data.time, aircraft = filtered)
    }

    @GetMapping("/track")
    suspend fun getTrack(@RequestParam icao24: String): TrackResponse {
        val now = System.currentTimeMillis()
        val key = icao24.lowercase()

        // Return cached track if fresh
        cachedTracks[key]?.let { (ts, resp) ->
            if ((now - ts) < TRACK_CACHE_MS) return resp
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
                val empty = TrackResponse(icao24 = icao24, callsign = "", path = emptyList())
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

            val result = TrackResponse(
                icao24 = response.icao24,
                callsign = response.callsign?.trim() ?: "",
                path = waypoints
            )
            cachedTracks[key] = now to result

            // Evict old track cache entries
            if (cachedTracks.size > 100) {
                val cutoff = now - TRACK_CACHE_MS * 10
                cachedTracks.entries.removeIf { it.value.first < cutoff }
            }

            result
        } catch (e: Exception) {
            log.error("OpenSky track fetch failed for $icao24: ${e.message}")
            cachedTracks[key]?.second ?: TrackResponse(icao24 = icao24, callsign = "", path = emptyList())
        }
    }
}

// --- DTOs ---

@JsonIgnoreProperties(ignoreUnknown = true)
data class OpenSkyResponse(
    val time: Long = 0,
    val states: List<List<Any?>>? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class OpenSkyTrackResponse(
    val icao24: String = "",
    val callsign: String? = null,
    val startTime: Long = 0,
    val endTime: Long = 0,
    val path: List<List<Any?>>? = null
)

data class AircraftResponse(
    val time: Long,
    val aircraft: List<AircraftState>
)

data class AircraftState(
    val icao24: String,
    val callsign: String,
    val originCountry: String,
    val longitude: Double,
    val latitude: Double,
    val altitude: Double?,
    val velocity: Double?,
    val heading: Double?,
    val verticalRate: Double?
)

data class TrackResponse(
    val icao24: String,
    val callsign: String,
    val path: List<TrackWaypoint>
)

data class TrackWaypoint(
    val time: Long,
    val latitude: Double,
    val longitude: Double,
    val altitude: Double?,
    val heading: Double?,
    val onGround: Boolean
)
