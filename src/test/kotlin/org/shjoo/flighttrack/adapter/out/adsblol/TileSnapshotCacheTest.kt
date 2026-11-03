package org.shjoo.flighttrack.adapter.out.adsblol

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertSame

@OptIn(ExperimentalCoroutinesApi::class)
class TileSnapshotCacheTest {

    private val korea = TileId(7, 38)
    private val other = TileId(6, 50)
    private val third = TileId(6, 51)

    private class FakeFetch(private val scope: TestScope, private val latencyMs: Long = 0) {
        var calls = 0
        var failing = false
        suspend fun invoke(id: TileId): TileData? {
            calls++
            if (latencyMs > 0) delay(latencyMs)
            return if (failing) null else TileData(scope.testScheduler.currentTime, emptyList())
        }
    }

    private fun TestScope.cache(fetch: FakeFetch) =
        TileSnapshotCache(clockMs = { testScheduler.currentTime }, fetch = fetch::invoke)

    @Test
    fun `a second request within the TTL does not fetch again`() = runTest {
        val fetch = FakeFetch(this)
        val cache = cache(fetch)
        val first = cache.get(listOf(korea)).single()
        delay(9_000)
        assertSame(first, cache.get(listOf(korea)).single())
        assertEquals(1, fetch.calls)
        delay(1_500)
        cache.get(listOf(korea))
        assertEquals(2, fetch.calls)
    }

    @Test
    fun `concurrent requests for a stale tile fetch it once`() = runTest {
        val fetch = FakeFetch(this, latencyMs = 500)
        val cache = cache(fetch)
        val results = (1..5).map { async { cache.get(listOf(korea)).single() } }.awaitAll()
        assertEquals(1, fetch.calls)
        assertEquals(1, results.toSet().size)
    }

    @Test
    fun `a failed refetch serves the stale tile without advancing its fetch time`() = runTest {
        val fetch = FakeFetch(this)
        val cache = cache(fetch)
        cache.get(listOf(korea))
        delay(15_000)
        fetch.failing = true
        val stale = assertNotNull(cache.get(listOf(korea)).single())
        assertEquals(0L, stale.fetchedAtMs)
        assertEquals(2, fetch.calls)
    }

    @Test
    fun `a tile older than two minutes is not served when the refetch fails`() = runTest {
        val fetch = FakeFetch(this)
        val cache = cache(fetch)
        cache.get(listOf(korea))
        delay(121_000)
        fetch.failing = true
        assertNull(cache.get(listOf(korea)).single())
    }

    @Test
    fun `a tile that never fetched successfully is null`() = runTest {
        val fetch = FakeFetch(this).apply { failing = true }
        assertEquals(listOf<TileData?>(null), cache(fetch).get(listOf(korea)))
    }

    @Test
    fun `tiles are fetched concurrently`() = runTest {
        val fetch = FakeFetch(this, latencyMs = 1_000)
        val results = cache(fetch).get(listOf(korea, other, third))
        assertEquals(3, results.filterNotNull().size)
        assertEquals(1_000L, testScheduler.currentTime)
    }
}
