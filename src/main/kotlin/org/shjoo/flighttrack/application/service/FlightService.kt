package org.shjoo.flighttrack.application.service

import org.shjoo.flighttrack.domain.model.Flight
import org.shjoo.flighttrack.domain.port.`in`.GetFlightDetailUseCase
import org.shjoo.flighttrack.domain.port.`in`.SearchFlightsUseCase
import org.shjoo.flighttrack.domain.port.out.FlightCachePort
import org.shjoo.flighttrack.domain.port.out.FlightFallbackPort
import org.shjoo.flighttrack.domain.port.out.FlightSearchPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service

@Service
class FlightService(
    private val flightSearchPort: FlightSearchPort,
    private val flightCachePort: FlightCachePort,
    private val flightFallbackPort: FlightFallbackPort
) : SearchFlightsUseCase, GetFlightDetailUseCase {

    private val log = LoggerFactory.getLogger(FlightService::class.java)

    override suspend fun searchFlights(from: String, dateFrom: String, dateTo: String): List<Flight> {
        val cacheKey = flightCachePort.buildKey(from, dateFrom, dateTo)
        flightCachePort.get(cacheKey)?.let { return it }

        return try {
            val results = flightSearchPort.searchInspirationFlights(from, dateFrom)

            if (results.isNotEmpty()) {
                flightCachePort.put(cacheKey, results)
                results
            } else {
                log.warn("No results for from=$from, using fallback")
                flightFallbackPort.getFallback(from)
            }
        } catch (e: Exception) {
            log.error("Flight search failed: ${e.message}", e)
            flightFallbackPort.getFallback(from)
        }
    }

    override suspend fun getFlightDetail(from: String, to: String, departureDate: String): Flight? {
        return try {
            flightSearchPort.searchFlightOffers(from, to, departureDate)
        } catch (e: Exception) {
            log.error("Flight detail fetch failed: ${e.message}", e)
            null
        }
    }
}
