package org.shjoo.flighttrack.adapter.out.route

import kotlinx.coroutines.runBlocking
import org.shjoo.flighttrack.domain.model.FlightRoute
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CallsignRouteAdapterTest {

    private class FakeUpstreams {
        var routeset: () -> ClientResponse = { ClientResponse.create(HttpStatus.CREATED).build() }
        var adsbdb: () -> ClientResponse = { json(HttpStatus.NOT_FOUND, unknownCallsign) }
        val calls = mutableListOf<String>()

        fun client(baseUrl: String): WebClient = WebClient.builder()
            .baseUrl(baseUrl)
            .exchangeFunction { request ->
                val host = request.url().host
                calls += host
                Mono.just(if (host == "api.adsbdb.com") adsbdb() else routeset())
            }.build()

        fun count(host: String) = calls.count { it == host }
    }

    private companion object {
        const val ADSB_LOL = "api.adsb.lol"
        const val ADSBDB = "api.adsbdb.com"

        val unknownCallsign = """{"response":"unknown callsign"}"""
        val adsbdbCsz633 = """{"response":{"flightroute":{"callsign":"CSZ633","callsign_icao":"CSZ633","callsign_iata":"ZH633","airline":{"name":"Shenzhen Airlines","icao":"CSZ","iata":"ZH"},"origin":{"iata_code":"SZX","icao_code":"ZGSZ","name":"Shenzhen Bao'an International Airport"},"destination":{"iata_code":"ICN","icao_code":"RKSI","name":"Incheon International Airport"}}}}"""
        val routesetKal123 = """[{"callsign":"KAL123","_airports":[{"iata":"ICN","icao":"RKSI","name":"Incheon","lat":37.46,"lon":126.44},{"iata":"BNE","icao":"YBBN","name":"Brisbane","lat":-27.38,"lon":153.11}],"plausible":true}]"""

        fun json(status: HttpStatus, body: String): ClientResponse =
            ClientResponse.create(status)
                .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
                .body(body)
                .build()
    }

    private val upstreams = FakeUpstreams()
    private val adapter = CallsignRouteAdapter(
        upstreams.client("https://$ADSB_LOL"),
        upstreams.client("https://$ADSBDB"),
    )

    @Test
    fun `adsbdb answers when adsb_lol routeset is empty`() = runBlocking<Unit> {
        upstreams.adsbdb = { json(HttpStatus.OK, adsbdbCsz633) }
        assertEquals(FlightRoute("SZX", "ICN", plausible = true), adapter.resolveRoute("CSZ633", 34.1, 126.7))
        assertEquals(1, upstreams.count(ADSB_LOL))
        assertEquals(1, upstreams.count(ADSBDB))
    }

    @Test
    fun `a routeset hit is used without calling adsbdb`() = runBlocking<Unit> {
        upstreams.routeset = { json(HttpStatus.OK, routesetKal123) }
        upstreams.adsbdb = { error("adsbdb must not be called") }
        assertEquals(FlightRoute("ICN", "BNE", plausible = true), adapter.resolveRoute("KAL123", null, null))
        assertEquals(0, upstreams.count(ADSBDB))
    }

    @Test
    fun `a miss from both is cached so the second call makes no request`() = runBlocking<Unit> {
        assertNull(adapter.resolveRoute("ZZZZ999", null, null))
        assertNull(adapter.resolveRoute("ZZZZ999", null, null))
        assertEquals(1, upstreams.count(ADSB_LOL))
        assertEquals(1, upstreams.count(ADSBDB))
    }

    @Test
    fun `an adsb_lol failure still lets adsbdb answer`() = runBlocking<Unit> {
        upstreams.routeset = { json(HttpStatus.INTERNAL_SERVER_ERROR, "{}") }
        upstreams.adsbdb = { json(HttpStatus.OK, adsbdbCsz633) }
        assertEquals(FlightRoute("SZX", "ICN", plausible = true), adapter.resolveRoute("CSZ633", null, null))
    }
}
