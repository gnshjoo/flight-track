package org.shjoo.flighttrack.adapter.out.openflights

import org.shjoo.flighttrack.domain.model.Airport

data class RouteKey(val airlineIata: String, val departureIata: String)

class OpenFlightsData(
    val airlineIataByIcao: Map<String, String>,
    val airlineNameByIcao: Map<String, String>,
    val destinationsByRoute: Map<RouteKey, List<String>>,
    val airports: Map<String, Airport>,
) {
    companion object {
        fun parse(airlinesCsv: String, routesDat: String, airportsDat: String): OpenFlightsData {
            val iataByIcao = mutableMapOf<String, String>()
            val nameByIcao = mutableMapOf<String, String>()
            for (line in airlinesCsv.lines()) {
                if (line.startsWith("iata_code") || line.isBlank()) continue
                val cols = line.split("^")
                if (cols.size < 3) continue
                val iata = cols[0].trim()
                val icao = cols[1].trim()
                if (iata.isNotBlank() && icao.isNotBlank()) {
                    iataByIcao[icao] = iata
                    nameByIcao[icao] = cols[2].trim()
                }
            }

            val destinations = mutableMapOf<RouteKey, MutableList<String>>()
            for (line in routesDat.lines()) {
                val cols = parseCsvLine(line)
                if (cols.size < 5) continue
                val airline = cols[0].trim()
                val src = cols[2].trim()
                val dst = cols[4].trim()
                if (airline.isNotBlank() && src.isNotBlank() && dst.isNotBlank()
                    && airline != "\\N" && src != "\\N" && dst != "\\N") {
                    destinations.getOrPut(RouteKey(airline, src)) { mutableListOf() }.add(dst)
                }
            }

            // cols: 0=id, 1=name, 2=city, 3=country, 4=iata, 5=icao, 6=lat, 7=lng, ...
            val airports = mutableMapOf<String, Airport>()
            for (line in airportsDat.lines()) {
                val cols = parseCsvLine(line)
                if (cols.size < 8) continue
                val name = cols[1].trim().removeSurrounding("\"")
                val iata = cols[4].trim().removeSurrounding("\"")
                if (iata.isBlank() || iata == "\\N" || iata.length != 3) continue
                val lat = cols[6].trim().removeSurrounding("\"").toDoubleOrNull()
                val lng = cols[7].trim().removeSurrounding("\"").toDoubleOrNull()
                if (lat != null && lng != null) {
                    airports[iata] = Airport(iata = iata, name = name, lat = lat, lng = lng)
                }
            }
            return OpenFlightsData(iataByIcao, nameByIcao, destinations, airports)
        }

        fun loadBundled(): OpenFlightsData {
            val data = parse(
                airlinesCsv = readResource("iata_airlines.csv"),
                routesDat = readResource("routes.dat"),
                airportsDat = readResource("airports.dat"),
            )
            check(data.airlineIataByIcao.isNotEmpty() && data.destinationsByRoute.isNotEmpty() && data.airports.isNotEmpty()) {
                "OpenFlights data is empty"
            }
            return data
        }

        private fun readResource(name: String): String {
            val stream = OpenFlightsData::class.java.getResourceAsStream("/openflights/$name")
                ?: throw IllegalStateException("Missing classpath resource /openflights/$name")
            return stream.use { it.readBytes().toString(Charsets.UTF_8) }
        }

        private fun parseCsvLine(line: String): List<String> {
            val result = mutableListOf<String>()
            var current = StringBuilder()
            var inQuotes = false
            for (ch in line) {
                when {
                    ch == '"' -> inQuotes = !inQuotes
                    ch == ',' && !inQuotes -> { result.add(current.toString()); current = StringBuilder() }
                    else -> current.append(ch)
                }
            }
            result.add(current.toString())
            return result
        }
    }
}
