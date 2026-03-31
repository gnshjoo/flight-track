package org.shjoo.flighttrack.adapter.out.amadeus

import org.shjoo.flighttrack.adapter.out.amadeus.dto.*
import org.shjoo.flighttrack.domain.model.Flight
import org.shjoo.flighttrack.domain.model.Leg
import org.shjoo.flighttrack.domain.port.out.FlightSearchPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBodyOrNull
import java.util.concurrent.ConcurrentHashMap

@Component
class AmadeusFlightAdapter(
    private val amadeusWebClient: WebClient,
    private val amadeusAuthAdapter: AmadeusAuthAdapter
) : FlightSearchPort {

    private val log = LoggerFactory.getLogger(AmadeusFlightAdapter::class.java)

    private val airportCoords = ConcurrentHashMap<String, Pair<Double, Double>>().apply {
        put("BKK", 13.6900 to 100.7501); put("MNL", 14.5995 to 120.9842)
        put("SGN", 10.8231 to 106.6297); put("NRT", 35.7720 to 140.3929)
        put("KIX", 34.4347 to 135.2440); put("TPE", 25.0797 to 121.2342)
        put("HKG", 22.3080 to 113.9185); put("SIN", 1.3644 to 103.9915)
        put("KUL", 2.7456 to 101.7099); put("IST", 41.2753 to 28.7519)
        put("LHR", 51.4700 to -0.4543); put("CDG", 49.0097 to 2.5479)
        put("FCO", 41.8003 to 12.2389); put("BCN", 41.2974 to 2.0833)
        put("DXB", 25.2532 to 55.3657); put("SYD", -33.9399 to 151.1753)
        put("HAN", 21.2187 to 105.8044); put("DPS", -8.7467 to 115.1670)
        put("DAD", 16.0439 to 108.1989); put("FUK", 33.5902 to 130.4017)
        put("CTS", 42.7752 to 141.6922); put("PEK", 40.0799 to 116.6031)
        put("PVG", 31.1443 to 121.8083); put("BOM", 19.0896 to 72.8656)
        put("DEL", 28.5562 to 77.1000); put("VVO", 43.3950 to 132.1444)
        put("GUM", 13.4835 to 144.7961); put("PNH", 11.5464 to 104.8440)
        put("CEB", 10.3075 to 123.9794); put("CNX", 18.7669 to 98.9625)
        put("ICN", 37.4602 to 126.4407); put("JFK", 40.6413 to -73.7781)
        put("LAX", 33.9416 to -118.4085); put("ORD", 41.9742 to -87.9073)
        put("ATL", 33.6407 to -84.4277); put("DFW", 32.8998 to -97.0403)
        put("FRA", 50.0379 to 8.5622); put("AMS", 52.3105 to 4.7683)
        put("MAD", 40.4983 to -3.5676); put("MUC", 48.3537 to 11.7750)
        put("DOH", 25.2731 to 51.6081); put("HND", 35.5494 to 139.7798)
        put("SFO", 37.6213 to -122.3790); put("MIA", 25.7959 to -80.2870)
        put("YYZ", 43.6777 to -79.6248); put("MEX", 19.4361 to -99.0719)
        put("GRU", -23.4356 to -46.4731); put("EZE", -34.8222 to -58.5358)
        put("JNB", -26.1392 to 28.2460); put("CAI", 30.1219 to 31.4056)
        put("NBO", -1.3192 to 36.9278); put("ADD", 8.9779 to 38.7993)
    }

    override suspend fun searchInspirationFlights(from: String, departureDate: String): List<Flight> {
        val token = amadeusAuthAdapter.getAccessToken()

        val response = amadeusWebClient.get()
            .uri { builder ->
                builder.path("/v1/shopping/flight-destinations")
                    .queryParam("origin", from)
                    .queryParam("departureDate", departureDate)
                    .queryParam("oneWay", true)
                    .queryParam("maxPrice", 2000)
                    .build()
            }
            .header("Authorization", "Bearer $token")
            .retrieve()
            .awaitBodyOrNull<AmadeusInspirationResponse>()

        if (response == null || response.data.isEmpty()) {
            return emptyList()
        }

        return response.data
            .mapNotNull { dest ->
                val coords = getCoordinates(dest.destination, token) ?: return@mapNotNull null
                Flight(
                    destination = dest.destination,
                    iata = dest.destination,
                    lat = coords.first,
                    lng = coords.second,
                    price = dest.price.total.toDoubleOrNull()?.toInt() ?: 0,
                    directPrice = null,
                    legs = listOf(
                        Leg(
                            from = from,
                            to = dest.destination,
                            airline = "",
                            price = dest.price.total.toDoubleOrNull()?.toInt(),
                            departureTime = dest.departureDate,
                            arrivalTime = ""
                        )
                    ),
                    deepLink = null
                )
            }
            .sortedBy { it.price }
    }

    override suspend fun searchFlightOffers(from: String, to: String, departureDate: String): Flight? {
        val token = amadeusAuthAdapter.getAccessToken()

        val response = amadeusWebClient.get()
            .uri { builder ->
                builder.path("/v2/shopping/flight-offers")
                    .queryParam("originLocationCode", from)
                    .queryParam("destinationLocationCode", to)
                    .queryParam("departureDate", departureDate)
                    .queryParam("adults", 1)
                    .queryParam("currencyCode", "USD")
                    .queryParam("max", 5)
                    .queryParam("nonStop", false)
                    .build()
            }
            .header("Authorization", "Bearer $token")
            .retrieve()
            .awaitBodyOrNull<AmadeusFlightOffersResponse>()
            ?: return null

        val carriers = response.dictionaries?.carriers ?: emptyMap()
        val cheapest = response.data.minByOrNull {
            it.price.grandTotal.toDoubleOrNull() ?: Double.MAX_VALUE
        } ?: return null

        val directPrice = response.data
            .filter { it.itineraries.firstOrNull()?.segments?.size == 1 }
            .minByOrNull { it.price.grandTotal.toDoubleOrNull() ?: Double.MAX_VALUE }
            ?.price?.grandTotal?.toDoubleOrNull()?.toInt()

        val cheapestPrice = cheapest.price.grandTotal.toDoubleOrNull()?.toInt() ?: 0
        val coords = getCoordinates(to, token)

        val legs = cheapest.itineraries.firstOrNull()?.segments?.map { seg ->
            val airlineName = carriers[seg.carrierCode] ?: seg.carrierCode
            Leg(
                from = seg.departure.iataCode,
                to = seg.arrival.iataCode,
                airline = airlineName,
                price = null,
                departureTime = seg.departure.at,
                arrivalTime = seg.arrival.at
            )
        } ?: emptyList()

        return Flight(
            destination = to,
            iata = to,
            lat = coords?.first ?: 0.0,
            lng = coords?.second ?: 0.0,
            price = cheapestPrice,
            directPrice = if (directPrice != null && directPrice > cheapestPrice) directPrice else null,
            legs = legs,
            deepLink = null
        )
    }

    private suspend fun getCoordinates(iata: String, token: String): Pair<Double, Double>? {
        airportCoords[iata]?.let { return it }

        return try {
            val response = amadeusWebClient.get()
                .uri { builder ->
                    builder.path("/v1/reference-data/locations")
                        .queryParam("subType", "AIRPORT")
                        .queryParam("keyword", iata)
                        .build()
                }
                .header("Authorization", "Bearer $token")
                .retrieve()
                .awaitBodyOrNull<AmadeusLocationResponse>()

            val loc = response?.data?.firstOrNull { it.iataCode == iata }
            if (loc?.geoCode != null) {
                val coords = loc.geoCode.latitude to loc.geoCode.longitude
                airportCoords[iata] = coords
                coords
            } else {
                null
            }
        } catch (e: Exception) {
            log.warn("Failed to get coordinates for $iata: ${e.message}")
            null
        }
    }
}
