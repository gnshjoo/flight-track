package org.shjoo.flighttrack.adapter.`in`.web

import org.shjoo.flighttrack.domain.model.InvalidRequestException
import org.shjoo.flighttrack.domain.model.UpstreamUnavailableException
import org.springframework.test.web.reactive.server.WebTestClient
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController
import kotlin.test.Test

class ApiExceptionHandlerTest {

    @RestController
    class FakeController {
        @GetMapping("/invalid")
        fun invalid(): String = throw InvalidRequestException("bad bbox")

        @GetMapping("/upstream")
        fun upstream(): String = throw UpstreamUnavailableException("adsb")

        @GetMapping("/boom")
        fun boom(): String = throw IllegalStateException("secret internal detail")

        @GetMapping("/needs-number")
        fun needsNumber(@RequestParam lat: Double): String = "ok $lat"
    }

    private val client = WebTestClient.bindToController(FakeController())
        .controllerAdvice(ApiExceptionHandler())
        .build()

    @Test
    fun `invalid request is 400 with code and message`() {
        client.get().uri("/invalid").exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.code").isEqualTo("invalid_request")
            .jsonPath("$.message").isEqualTo("bad bbox")
    }

    @Test
    fun `missing query param is 400 in the same body shape`() {
        client.get().uri("/needs-number").exchange()
            .expectStatus().isBadRequest
            .expectBody()
            .jsonPath("$.code").isEqualTo("invalid_request")
            .jsonPath("$.message").isNotEmpty
    }

    @Test
    fun `non-numeric query param is 400`() {
        client.get().uri("/needs-number?lat=abc").exchange()
            .expectStatus().isBadRequest
            .expectBody().jsonPath("$.code").isEqualTo("invalid_request")
    }

    @Test
    fun `upstream failure is 502`() {
        client.get().uri("/upstream").exchange()
            .expectStatus().isEqualTo(502)
            .expectBody().jsonPath("$.code").isEqualTo("upstream_unavailable")
    }

    @Test
    fun `unexpected error is 500 without leaking the exception message`() {
        client.get().uri("/boom").exchange()
            .expectStatus().is5xxServerError
            .expectBody()
            .jsonPath("$.code").isEqualTo("internal_error")
            .jsonPath("$.message").value<String> { assert("secret" !in it) }
    }

    @Test
    fun `unknown route keeps its 404`() {
        client.get().uri("/no-such-route").exchange().expectStatus().isNotFound
    }
}
