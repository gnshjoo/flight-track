package org.shjoo.flighttrack.adapter.out.cache

import org.shjoo.flighttrack.domain.model.Flight
import org.shjoo.flighttrack.domain.port.out.FlightCachePort
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

@Component
class InMemoryFlightCacheAdapter : FlightCachePort {

    private data class CacheEntry(
        val data: List<Flight>,
        val timestamp: Long
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val ttlMs = 10 * 60 * 1000L

    override fun get(key: String): List<Flight>? {
        val entry = cache[key] ?: return null
        if (System.currentTimeMillis() - entry.timestamp > ttlMs) {
            cache.remove(key)
            return null
        }
        return entry.data
    }

    override fun put(key: String, data: List<Flight>) {
        cache[key] = CacheEntry(data, System.currentTimeMillis())
    }

    override fun buildKey(from: String, dateFrom: String, dateTo: String): String =
        "$from|$dateFrom|$dateTo"

    @Scheduled(fixedRate = 600_000)
    fun evictExpired() {
        val now = System.currentTimeMillis()
        cache.entries.removeIf { now - it.value.timestamp > ttlMs }
    }
}
