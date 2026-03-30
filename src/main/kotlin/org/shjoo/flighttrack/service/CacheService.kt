package org.shjoo.flighttrack.service

import org.shjoo.flighttrack.dto.FlightResponse
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Service
import java.util.concurrent.ConcurrentHashMap

@Service
class CacheService {

    private data class CacheEntry(
        val data: List<FlightResponse>,
        val timestamp: Long
    )

    private val cache = ConcurrentHashMap<String, CacheEntry>()
    private val ttlMs = 10 * 60 * 1000L // 10 minutes

    fun get(key: String): List<FlightResponse>? {
        val entry = cache[key] ?: return null
        if (System.currentTimeMillis() - entry.timestamp > ttlMs) {
            cache.remove(key)
            return null
        }
        return entry.data
    }

    fun put(key: String, data: List<FlightResponse>) {
        cache[key] = CacheEntry(data, System.currentTimeMillis())
    }

    fun buildKey(from: String, dateFrom: String, dateTo: String): String =
        "$from|$dateFrom|$dateTo"

    @Scheduled(fixedRate = 600_000) // every 10 minutes
    fun evictExpired() {
        val now = System.currentTimeMillis()
        cache.entries.removeIf { now - it.value.timestamp > ttlMs }
    }
}
