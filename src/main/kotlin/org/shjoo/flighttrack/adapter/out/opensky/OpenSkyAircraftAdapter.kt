package org.shjoo.flighttrack.adapter.out.opensky

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.shjoo.flighttrack.adapter.out.opensky.dto.OpenSkyStatesResponse
import org.shjoo.flighttrack.adapter.out.opensky.dto.OpenSkyTrackResponse
import org.shjoo.flighttrack.domain.model.AircraftSnapshot
import org.shjoo.flighttrack.domain.model.AircraftState
import org.shjoo.flighttrack.domain.model.Track
import org.shjoo.flighttrack.domain.model.TrackWaypoint
import org.shjoo.flighttrack.domain.port.out.AircraftTrackPort
import org.shjoo.flighttrack.domain.port.out.AircraftTrackingPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodyOrNull

@Component
class OpenSkyAircraftAdapter : AircraftTrackingPort, AircraftTrackPort {

    private val log = LoggerFactory.getLogger(OpenSkyAircraftAdapter::class.java)

    private val openskyClient = WebClient.builder()
        .baseUrl("https://opensky-network.org")
        .codecs { it.defaultCodecs().maxInMemorySize(10 * 1024 * 1024) }
        .build()

    @Volatile private var cachedSnapshot: AircraftSnapshot? = null
    @Volatile private var cachedSnapshotTime: Long = 0
    private val SNAPSHOT_CACHE_MS = 30_000L
    private val fetchMutex = Mutex()

    @Volatile private var cachedTracks = mutableMapOf<String, Pair<Long, Track>>()
    private val TRACK_CACHE_MS = 30_000L

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
                result
            } catch (e: Exception) {
                log.error("OpenSky API failed: ${e.message}")
                cachedSnapshot ?: AircraftSnapshot(time = System.currentTimeMillis() / 1000, aircraft = emptyList())
            }
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

            val result = Track(
                icao24 = response.icao24,
                callsign = response.callsign?.trim() ?: "",
                path = waypoints
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
}
