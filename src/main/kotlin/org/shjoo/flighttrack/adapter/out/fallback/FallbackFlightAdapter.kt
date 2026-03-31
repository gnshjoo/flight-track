package org.shjoo.flighttrack.adapter.out.fallback

import org.shjoo.flighttrack.domain.model.Flight
import org.shjoo.flighttrack.domain.model.Leg
import org.shjoo.flighttrack.domain.port.out.FlightFallbackPort
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class FallbackFlightAdapter : FlightFallbackPort {

    private val log = LoggerFactory.getLogger(FallbackFlightAdapter::class.java)

    override fun getFallback(from: String): List<Flight> {
        log.info("Using fallback data for from=$from")
        return if (from.equals("ICN", ignoreCase = true)) {
            getSeoulFallback()
        } else {
            emptyList()
        }
    }

    private fun getSeoulFallback(): List<Flight> = listOf(
        flight("Bangkok", "BKK", 13.6900, 100.7501, 189, 320, listOf(
            leg("ICN", "BKK", "AirAsia X", 189, "08:00", "12:30")
        )),
        flight("Manila", "MNL", 14.5995, 120.9842, 165, 290, listOf(
            leg("ICN", "MNL", "Cebu Pacific", 165, "09:00", "13:00")
        )),
        flight("Ho Chi Minh City", "SGN", 10.8231, 106.6297, 210, 350, listOf(
            leg("ICN", "SGN", "VietJet", 210, "10:00", "14:30")
        )),
        flight("Tokyo", "NRT", 35.7720, 140.3929, 180, null, listOf(
            leg("ICN", "NRT", "Peach", 180, "07:00", "09:30")
        )),
        flight("Osaka", "KIX", 34.4347, 135.2440, 155, null, listOf(
            leg("ICN", "KIX", "Jeju Air", 155, "08:30", "10:30")
        )),
        flight("Taipei", "TPE", 25.0797, 121.2342, 145, null, listOf(
            leg("ICN", "TPE", "T'way Air", 145, "09:00", "11:00")
        )),
        flight("Hong Kong", "HKG", 22.3080, 113.9185, 220, 380, listOf(
            leg("ICN", "HKG", "HK Express", 220, "11:00", "14:00")
        )),
        flight("Singapore", "SIN", 1.3644, 103.9915, 280, 450, listOf(
            leg("ICN", "SIN", "Scoot", 280, "23:00", "05:30")
        )),
        flight("Kuala Lumpur", "KUL", 2.7456, 101.7099, 240, 400, listOf(
            leg("ICN", "KUL", "AirAsia X", 240, "22:00", "04:00")
        )),
        flight("Istanbul", "IST", 41.2753, 28.7519, 420, 720, listOf(
            leg("ICN", "BKK", "AirAsia X", 89, "08:00", "12:30"),
            leg("BKK", "IST", "Turkish Airlines", 331, "14:00", "20:00")
        )),
        flight("London", "LHR", 51.4700, -0.4543, 580, 950, listOf(
            leg("ICN", "IST", "Turkish Airlines", 280, "10:00", "16:00"),
            leg("IST", "LHR", "Turkish Airlines", 300, "18:00", "20:00")
        )),
        flight("Paris", "CDG", 49.0097, 2.5479, 550, 880, listOf(
            leg("ICN", "HEL", "Finnair", 250, "09:00", "14:00"),
            leg("HEL", "CDG", "Finnair", 300, "16:00", "18:30")
        )),
        flight("Rome", "FCO", 41.8003, 12.2389, 490, 780, listOf(
            leg("ICN", "IST", "Turkish Airlines", 280, "10:00", "16:00"),
            leg("IST", "FCO", "Pegasus", 210, "18:00", "20:00")
        )),
        flight("Barcelona", "BCN", 41.2974, 2.0833, 520, 850, listOf(
            leg("ICN", "IST", "Turkish Airlines", 280, "10:00", "16:00"),
            leg("IST", "BCN", "Pegasus", 240, "18:30", "21:00")
        )),
        flight("Dubai", "DXB", 25.2532, 55.3657, 380, 550, listOf(
            leg("ICN", "DXB", "flydubai", 380, "23:00", "05:00")
        )),
        flight("Sydney", "SYD", -33.9399, 151.1753, 620, 1100, listOf(
            leg("ICN", "SIN", "Scoot", 180, "23:00", "05:30"),
            leg("SIN", "SYD", "Jetstar", 440, "08:00", "18:00")
        )),
        flight("Hanoi", "HAN", 21.2187, 105.8044, 195, 330, listOf(
            leg("ICN", "HAN", "VietJet", 195, "08:00", "11:30")
        )),
        flight("Bali", "DPS", -8.7467, 115.1670, 310, 520, listOf(
            leg("ICN", "SIN", "Scoot", 150, "23:00", "05:30"),
            leg("SIN", "DPS", "Scoot", 160, "08:00", "11:00")
        )),
        flight("Da Nang", "DAD", 16.0439, 108.1989, 170, 280, listOf(
            leg("ICN", "DAD", "VietJet", 170, "09:30", "13:00")
        )),
        flight("Fukuoka", "FUK", 33.5902, 130.4017, 120, null, listOf(
            leg("ICN", "FUK", "Jin Air", 120, "10:00", "11:30")
        )),
        flight("Sapporo", "CTS", 42.7752, 141.6922, 200, null, listOf(
            leg("ICN", "CTS", "Jeju Air", 200, "08:00", "11:00")
        )),
        flight("Beijing", "PEK", 40.0799, 116.6031, 250, 380, listOf(
            leg("ICN", "PEK", "Air China", 250, "09:00", "10:30")
        )),
        flight("Shanghai", "PVG", 31.1443, 121.8083, 220, 350, listOf(
            leg("ICN", "PVG", "Spring Airlines", 220, "10:00", "11:30")
        )),
        flight("Mumbai", "BOM", 19.0896, 72.8656, 380, 620, listOf(
            leg("ICN", "BKK", "AirAsia X", 89, "08:00", "12:30"),
            leg("BKK", "BOM", "IndiGo", 291, "14:30", "18:00")
        )),
        flight("Delhi", "DEL", 28.5562, 77.1000, 360, 580, listOf(
            leg("ICN", "BKK", "AirAsia X", 89, "08:00", "12:30"),
            leg("BKK", "DEL", "IndiGo", 271, "14:30", "18:00")
        )),
        flight("Vladivostok", "VVO", 43.3950, 132.1444, 180, null, listOf(
            leg("ICN", "VVO", "Aurora", 180, "14:00", "17:00")
        )),
        flight("Guam", "GUM", 13.4835, 144.7961, 280, null, listOf(
            leg("ICN", "GUM", "Jeju Air", 280, "22:00", "03:30")
        )),
        flight("Phnom Penh", "PNH", 11.5464, 104.8440, 230, 380, listOf(
            leg("ICN", "PNH", "AirAsia", 230, "08:00", "12:00")
        )),
        flight("Cebu", "CEB", 10.3075, 123.9794, 190, 320, listOf(
            leg("ICN", "CEB", "Cebu Pacific", 190, "09:00", "13:30")
        )),
        flight("Chiang Mai", "CNX", 18.7669, 98.9625, 210, 360, listOf(
            leg("ICN", "BKK", "AirAsia X", 89, "08:00", "12:30"),
            leg("BKK", "CNX", "Nok Air", 121, "14:00", "15:30")
        ))
    )

    private fun flight(
        destination: String, iata: String, lat: Double, lng: Double,
        price: Int, directPrice: Int?, legs: List<Leg>
    ) = Flight(destination = destination, iata = iata, lat = lat, lng = lng,
        price = price, directPrice = directPrice, legs = legs, deepLink = null)

    private fun leg(from: String, to: String, airline: String, price: Int, dep: String, arr: String) =
        Leg(from = from, to = to, airline = airline, price = price, departureTime = dep, arrivalTime = arr)
}
