package org.shjoo.flighttrack.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "amadeus")
data class AmadeusConfig(
    val clientId: String,
    val clientSecret: String,
    val baseUrl: String
)
