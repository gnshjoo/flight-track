package org.shjoo.flighttrack

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class FlightTrackApplication

fun main(args: Array<String>) {
    runApplication<FlightTrackApplication>(*args)
}
