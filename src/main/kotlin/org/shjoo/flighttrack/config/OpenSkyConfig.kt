package org.shjoo.flighttrack.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "opensky")
data class OpenSkyConfig(
    val baseUrl: String = "https://opensky-network.org",
    val username: String = "",
    val password: String = ""
) {
    val hasCredentials: Boolean get() = username.isNotBlank() && password.isNotBlank()
}
