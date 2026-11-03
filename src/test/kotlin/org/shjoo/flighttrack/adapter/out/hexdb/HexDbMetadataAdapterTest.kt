package org.shjoo.flighttrack.adapter.out.hexdb

import kotlinx.coroutines.runBlocking
import org.shjoo.flighttrack.domain.model.Icao24
import org.shjoo.flighttrack.domain.model.UpstreamUnavailableException
import org.springframework.http.HttpStatus
import org.springframework.http.MediaType
import org.springframework.web.reactive.function.client.ClientResponse
import org.springframework.web.reactive.function.client.WebClient
import reactor.core.publisher.Mono
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import java.time.Duration
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull

class HexDbMetadataAdapterTest {

    private class MutableClock(var now: Instant = Instant.parse("2026-01-01T00:00:00Z")) : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?) = this
        override fun instant(): Instant = now
        fun advance(d: Duration) { now = now.plus(d) }
    }

    private class FakeHexDb(var respond: () -> ClientResponse) {
        val paths = mutableListOf<String>()
        fun client(): WebClient = WebClient.builder().exchangeFunction { request ->
            paths += request.url().path
            Mono.just(respond())
        }.build()
    }

    private fun json(status: HttpStatus, body: String): ClientResponse =
        ClientResponse.create(status)
            .header("Content-Type", MediaType.APPLICATION_JSON_VALUE)
            .body(body)
            .build()

    private val found = """{"ModeS":"780AD2","Registration":"B-2447","Manufacturer":"Airbus","ICAOTypeCode":"A333","Type":"A330-343","RegisteredOwners":"Air China","OperatorFlagCode":"CCA"}"""
    private val notFound = """{"status":"404","error":"Aircraft not found."}"""
    private val hex = Icao24.parse("780ad2")

    private val clock = MutableClock()
    private val hexDb = FakeHexDb { json(HttpStatus.OK, found) }
    private val adapter = HexDbMetadataAdapter(hexDb.client(), clock)

    @Test
    fun `a 404 is cached so the second call makes no request`() = runBlocking<Unit> {
        hexDb.respond = { json(HttpStatus.NOT_FOUND, notFound) }
        assertNull(adapter.fetchAircraftMetadata(hex))
        assertNull(adapter.fetchAircraftMetadata(hex))
        assertEquals(1, hexDb.paths.size)
    }

    @Test
    fun `a hit is served from cache within five minutes and refetched after`() = runBlocking<Unit> {
        assertEquals("B-2447", adapter.fetchAircraftMetadata(hex)?.registration)
        clock.advance(Duration.ofMinutes(4))
        adapter.fetchAircraftMetadata(hex)
        assertEquals(1, hexDb.paths.size)
        clock.advance(Duration.ofMinutes(2))
        adapter.fetchAircraftMetadata(hex)
        assertEquals(2, hexDb.paths.size)
    }

    @Test
    fun `a server error is not cached`() = runBlocking<Unit> {
        hexDb.respond = { json(HttpStatus.INTERNAL_SERVER_ERROR, "{}") }
        assertFailsWith<UpstreamUnavailableException> { adapter.fetchAircraftMetadata(hex) }
        hexDb.respond = { json(HttpStatus.OK, found) }
        assertEquals("A330-343", adapter.fetchAircraftMetadata(hex)?.model)
        assertEquals(2, hexDb.paths.size)
    }

    @Test
    fun `a server error serves the stale entry when one exists`() = runBlocking<Unit> {
        adapter.fetchAircraftMetadata(hex)
        clock.advance(Duration.ofMinutes(10))
        hexDb.respond = { json(HttpStatus.BAD_GATEWAY, "{}") }
        assertEquals("B-2447", adapter.fetchAircraftMetadata(hex)?.registration)
    }

    @Test
    fun `a server error with no stale entry is upstream unavailable`() = runBlocking<Unit> {
        hexDb.respond = { json(HttpStatus.SERVICE_UNAVAILABLE, "{}") }
        assertFailsWith<UpstreamUnavailableException> { adapter.fetchAircraftMetadata(hex) }
    }

    @Test
    fun `the request path uses the uppercase hex`() = runBlocking<Unit> {
        adapter.fetchAircraftMetadata(hex)
        assertEquals(listOf("/api/v1/aircraft/780AD2"), hexDb.paths)
    }
}
