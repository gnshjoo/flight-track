package org.shjoo.flighttrack.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

// --- Flight Inspiration Search ---

@JsonIgnoreProperties(ignoreUnknown = true)
data class AmadeusInspirationResponse(
    val data: List<AmadeusDestination> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AmadeusDestination(
    val origin: String = "",
    val destination: String = "",
    val departureDate: String = "",
    val returnDate: String? = null,
    val price: AmadeusPrice = AmadeusPrice()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AmadeusPrice(
    val total: String = "0"
)

// --- Flight Offers Search (detail) ---

@JsonIgnoreProperties(ignoreUnknown = true)
data class AmadeusFlightOffersResponse(
    val data: List<AmadeusFlightOffer> = emptyList(),
    val dictionaries: AmadeusDictionaries? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AmadeusFlightOffer(
    val id: String = "",
    val itineraries: List<AmadeusItinerary> = emptyList(),
    val price: AmadeusOfferPrice = AmadeusOfferPrice(),
    val numberOfBookableSeats: Int? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AmadeusItinerary(
    val duration: String = "",
    val segments: List<AmadeusSegment> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AmadeusSegment(
    val departure: AmadeusEndpoint = AmadeusEndpoint(),
    val arrival: AmadeusEndpoint = AmadeusEndpoint(),
    val carrierCode: String = "",
    val number: String = "",
    val numberOfStops: Int = 0,
    val duration: String = ""
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AmadeusEndpoint(
    val iataCode: String = "",
    val terminal: String? = null,
    val at: String = ""
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AmadeusOfferPrice(
    val currency: String = "USD",
    val total: String = "0",
    val grandTotal: String = "0"
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AmadeusDictionaries(
    val carriers: Map<String, String> = emptyMap()
)

// --- Location / Airport Search ---

@JsonIgnoreProperties(ignoreUnknown = true)
data class AmadeusLocationResponse(
    val data: List<AmadeusLocationData> = emptyList()
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AmadeusLocationData(
    val iataCode: String = "",
    val name: String = "",
    val subType: String = "",
    val address: AmadeusAddress? = null,
    val geoCode: AmadeusGeoCode? = null
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AmadeusAddress(
    val cityName: String = "",
    val countryName: String = ""
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class AmadeusGeoCode(
    val latitude: Double = 0.0,
    val longitude: Double = 0.0
)
