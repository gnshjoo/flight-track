package org.shjoo.flighttrack.controller

import org.shjoo.flighttrack.service.AmadeusService
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api")
class AirportController(private val amadeusService: AmadeusService) {

    @GetMapping("/airports")
    suspend fun searchAirports(@RequestParam query: String): List<AirportResult> {
        if (query.length < 2) return emptyList()

        return try {
            val results = amadeusService.searchAirports(query)
            results.map { loc ->
                AirportResult(
                    name = loc.name,
                    city = loc.address?.cityName ?: "",
                    iata = loc.iataCode,
                    country = loc.address?.countryName ?: "",
                    lat = loc.geoCode?.latitude,
                    lng = loc.geoCode?.longitude
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    @GetMapping("/airports/nearest")
    fun nearestAirport(
        @RequestParam lat: Double,
        @RequestParam lng: Double
    ): AirportResult? {
        return amadeusService.findNearestAirport(lat, lng)?.let { (iata, coords) ->
            AirportResult(
                name = iata,
                city = "",
                iata = iata,
                country = "",
                lat = coords.first,
                lng = coords.second
            )
        }
    }
}

data class AirportResult(
    val name: String,
    val city: String,
    val iata: String,
    val country: String,
    val lat: Double?,
    val lng: Double?
)
