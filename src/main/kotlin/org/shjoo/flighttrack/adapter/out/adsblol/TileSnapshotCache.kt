package org.shjoo.flighttrack.adapter.out.adsblol

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.shjoo.flighttrack.domain.model.AircraftState
import java.util.concurrent.ConcurrentHashMap

internal class TileData(val fetchedAtMs: Long, val aircraft: List<AircraftState>)

/**
 * 타일별 스냅샷 캐시. 모든 사용자가 같은 격자 칸을 공유하므로 칸마다 한 번만 다시 받는다.
 * 격자 칸 수가 유한해 만료 엔트리를 지우지 않는다.
 */
internal class TileSnapshotCache(
    private val ttlMs: Long = 10_000,
    private val staleMaxMs: Long = 120_000,
    private val clockMs: () -> Long = System::currentTimeMillis,
    private val fetch: suspend (TileId) -> TileData?,
) {
    private class Slot {
        val mutex = Mutex()
        @Volatile var data: TileData? = null
        @Volatile var failedAtMs: Long? = null
    }

    private val slots = ConcurrentHashMap<TileId, Slot>()

    /** 요청 순서대로 타일당 결과 하나. null 은 지금 받지 못했고 쓸 만한 캐시도 없다는 뜻. */
    suspend fun get(tiles: List<TileId>): List<TileData?> = coroutineScope {
        tiles.map { async { get(it) } }.awaitAll()
    }

    private suspend fun get(id: TileId): TileData? {
        val slot = slots.computeIfAbsent(id) { Slot() }
        slot.data?.takeIf { isFresh(it) }?.let { return it }
        return slot.mutex.withLock {
            slot.data?.takeIf { isFresh(it) }?.let { return@withLock it }
            // 방금 실패한 칸을 기다리던 요청마다 다시 부르면 장애 중인 피드에 줄줄이 붙으므로 TTL 동안은 stale 로 답한다
            val failedAt = slot.failedAtMs
            if (failedAt != null && clockMs() - failedAt < ttlMs) return@withLock usableStale(slot)
            val fetched = fetch(id)
            if (fetched != null) {
                slot.data = fetched
                slot.failedAtMs = null
                fetched
            } else {
                slot.failedAtMs = clockMs()
                usableStale(slot)
            }
        }
    }

    private fun isFresh(data: TileData) = clockMs() - data.fetchedAtMs < ttlMs

    private fun usableStale(slot: Slot) = slot.data?.takeIf { clockMs() - it.fetchedAtMs < staleMaxMs }
}
