package org.shjoo.flighttrack.config

import org.shjoo.flighttrack.adapter.out.openflights.OpenFlightsData
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenFlightsConfig {

    @Bean
    fun openFlightsData(): OpenFlightsData = OpenFlightsData.loadBundled()
}
