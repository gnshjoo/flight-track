package org.shjoo.flighttrack.config

import io.swagger.v3.oas.models.OpenAPI
import io.swagger.v3.oas.models.info.Info
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class OpenApiConfig {

    @Bean
    fun openAPI(): OpenAPI = OpenAPI()
        .info(
            Info()
                .title("Flight Track API")
                .description("항공편 검색 및 실시간 항공기 추적 API")
                .version("1.0.0")
        )
}
