package org.shjoo.flighttrack.domain.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class GeoTypesTest {

    @Test
    fun `partial bbox is rejected naming the missing params`() {
        val e = assertFailsWith<InvalidRequestException> { BoundingBox.fromParams(33.0, null, 38.0, null) }
        assertEquals("lomin, lomax", e.message.orEmpty().substringAfter("missing: "))
    }

    @Test
    fun `all bbox params absent means default region`() {
        assertNull(BoundingBox.fromParams(null, null, null, null))
    }

    @Test
    fun `inverted bbox is rejected`() {
        assertFailsWith<InvalidRequestException> { BoundingBox.fromParams(38.0, 124.0, 33.0, 130.0) }
        assertFailsWith<InvalidRequestException> { BoundingBox.fromParams(33.0, 130.0, 38.0, 124.0) }
    }

    @Test
    fun `latitude outside 90 is rejected`() {
        assertFailsWith<InvalidRequestException> { BoundingBox.fromParams(-91.0, 124.0, 38.0, 130.0) }
        assertFailsWith<InvalidRequestException> { Coordinates.of(90.5, 0.0) }
    }

    @Test
    fun `longitude outside 180 and non-finite values are rejected`() {
        assertFailsWith<InvalidRequestException> { Coordinates.of(0.0, 200.0) }
        assertFailsWith<InvalidRequestException> { Coordinates.of(Double.NaN, 0.0) }
        assertFailsWith<InvalidRequestException> { Coordinates.of(0.0, Double.POSITIVE_INFINITY) }
    }

    @Test
    fun `icao24 must be six hex chars and is lowercased`() {
        assertEquals("780ad2", Icao24.parse(" 780AD2 ").hex)
        listOf("", "abc12", "abc1234", "abcxyz", "78 0ad").forEach { raw ->
            assertFailsWith<InvalidRequestException>(raw) { Icao24.parse(raw) }
        }
    }

    @Test
    fun `bbox contains points on its edge`() {
        val box = assertNotNull(BoundingBox.fromParams(33.0, 124.0, 38.0, 130.0))
        assertTrue(box.contains(33.0, 124.0))
        assertTrue(box.contains(38.0, 130.0))
        assertTrue(box.contains(35.0, 127.0))
        assertFalse(box.contains(38.01, 127.0))
        assertFalse(box.contains(35.0, 130.01))
    }
}
