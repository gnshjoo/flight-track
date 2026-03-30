package org.shjoo.flighttrack

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
class FlightTrackApplication

fun main(args: Array<String>) {
    runApplication<FlightTrackApplication>(*args)
}
