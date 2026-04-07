package org.shjoo.flighttrack

import org.shjoo.flighttrack.config.OpenSkyConfig
import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.EnableConfigurationProperties
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(OpenSkyConfig::class)
class FlightTrackApplication {

    private val log = LoggerFactory.getLogger(FlightTrackApplication::class.java)

    @Bean
    fun logConfig(openSkyConfig: OpenSkyConfig) = ApplicationRunner {
        log.info("=== Flight Track Configuration ===")
        log.info("OpenSky base-url: ${openSkyConfig.baseUrl}")
        log.info("OpenSky username: ${openSkyConfig.username.ifBlank { "(not set)" }}")
        log.info("OpenSky credentials: ${if (openSkyConfig.hasCredentials) "configured" else "not configured"}")
        log.info("==================================")
    }
}

fun main(args: Array<String>) {
    runApplication<FlightTrackApplication>(*args)
}
