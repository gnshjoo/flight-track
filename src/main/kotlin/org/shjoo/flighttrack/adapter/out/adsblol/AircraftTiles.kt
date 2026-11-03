package org.shjoo.flighttrack.adapter.out.adsblol

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.shjoo.flighttrack.adapter.out.adsblol.dto.AdsbLolV2Response
import org.shjoo.flighttrack.domain.model.AircraftSnapshot
import org.shjoo.flighttrack.domain.model.BoundingBox
import org.shjoo.flighttrack.domain.model.UpstreamUnavailableException

// bbox 미지정 시 기본 조회 지역: 한반도 중심 칸
private const val DEFAULT_LAT = 36.5
private const val DEFAULT_LON = 127.8

/**
 * bbox 를 고정 격자 칸으로 나눠 칸마다 모든 피드를 동시에 묻고 합친다.
 * [feeds] 는 원 하나를 조회해 응답이나 null 을 돌려주며, 동률이면 앞 피드가 이기므로 adsb.lol 을 먼저 둔다.
 */
internal class AircraftTiles(
    private val feeds: List<suspend (Circle) -> AdsbLolV2Response?>,
    private val clockMs: () -> Long = System::currentTimeMillis,
) {
    private val cache = TileSnapshotCache(clockMs = clockMs, fetch = ::fetchTile)

    suspend fun snapshot(box: BoundingBox?): AircraftSnapshot {
        val set = box?.let(::tilesFor) ?: TileSet(listOf(tileAt(DEFAULT_LAT, DEFAULT_LON)), partial = false)
        val data = cache.get(set.tiles)
        val present = data.filterNotNull()
        if (present.isEmpty() && set.tiles.isNotEmpty()) throw UpstreamUnavailableException("adsb")
        val nowMs = clockMs()
        return AircraftSnapshot(
            time = (present.minOfOrNull { it.fetchedAtMs } ?: nowMs) / 1000,
            aircraft = mergeFreshest(present.map { it.aircraft }),
            partial = set.partial || present.size < data.size,
            servedAt = nowMs / 1000.0,
        )
    }

    private suspend fun fetchTile(id: TileId): TileData? {
        val circle = id.circle()
        val responses = coroutineScope { feeds.map { feed -> async { feed(circle) } }.awaitAll() }.filterNotNull()
        if (responses.isEmpty()) return null
        return TileData(
            fetchedAtMs = clockMs(),
            aircraft = mergeFreshest(responses.map { r -> r.states.orEmpty().mapNotNull { it.toState(r.nowSeconds) } }),
        )
    }
}
