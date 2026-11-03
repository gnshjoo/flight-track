package org.shjoo.flighttrack

import org.slf4j.LoggerFactory
import org.springframework.boot.ApplicationRunner
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.context.annotation.Bean

@SpringBootApplication
class FlightTrackApplication {

    private val log = LoggerFactory.getLogger(FlightTrackApplication::class.java)

    @Bean
    fun logConfig() = ApplicationRunner {
        log.info("=== Flight Track Configuration ===")
        log.info("Tracking source: adsb.lol (https://api.adsb.lol)")
        log.info("Metadata source: hexdb.io")
        log.info("==================================")
    }
}

fun main(args: Array<String>) {
    runApplication<FlightTrackApplication>(*args)
}
