package org.shjoo.flighttrack.domain.model

class InvalidRequestException(message: String) : RuntimeException(message)

class UpstreamUnavailableException(source: String) : RuntimeException("upstream unavailable: $source")

@JvmInline
value class Icao24 private constructor(val hex: String) {
    companion object {
        private val PATTERN = Regex("^[0-9a-f]{6}$")

        fun parse(raw: String): Icao24 {
            val normalized = raw.trim().lowercase()
            if (!PATTERN.matches(normalized)) throw InvalidRequestException("icao24 must be six hex characters: '$raw'")
            return Icao24(normalized)
        }
    }
}

@ConsistentCopyVisibility
data class Coordinates private constructor(val lat: Double, val lng: Double) {
    companion object {
        fun of(lat: Double, lng: Double): Coordinates {
            if (!lat.isFinite() || lat !in -90.0..90.0) throw InvalidRequestException("lat must be within [-90, 90]: $lat")
            if (!lng.isFinite() || lng !in -180.0..180.0) throw InvalidRequestException("lng must be within [-180, 180]: $lng")
            return Coordinates(lat, lng)
        }
    }
}

class BoundingBox private constructor(val sw: Coordinates, val ne: Coordinates) {
    val lamin get() = sw.lat
    val lomin get() = sw.lng
    val lamax get() = ne.lat
    val lomax get() = ne.lng

    fun contains(lat: Double, lng: Double): Boolean =
        lat in lamin..lamax && lng in lomin..lomax

    companion object {
        fun fromParams(lamin: Double?, lomin: Double?, lamax: Double?, lomax: Double?): BoundingBox? {
            val params = mapOf("lamin" to lamin, "lomin" to lomin, "lamax" to lamax, "lomax" to lomax)
            val missing = params.filterValues { it == null }.keys
            if (missing.size == params.size) return null
            if (missing.isNotEmpty()) throw InvalidRequestException("bbox requires all of lamin, lomin, lamax, lomax; missing: ${missing.joinToString()}")

            val sw = Coordinates.of(lamin!!, lomin!!)
            val ne = Coordinates.of(lamax!!, lomax!!)
            if (sw.lat > ne.lat || sw.lng > ne.lng) throw InvalidRequestException("bbox min must not exceed max")
            return BoundingBox(sw, ne)
        }
    }
}
