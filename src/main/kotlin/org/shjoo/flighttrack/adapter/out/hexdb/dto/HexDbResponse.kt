package org.shjoo.flighttrack.adapter.out.hexdb.dto

import com.fasterxml.jackson.annotation.JsonIgnoreProperties

@JsonIgnoreProperties(ignoreUnknown = true)
data class HexDbAircraftResponse(
    val ModeS: String = "",
    val Registration: String? = null,
    val Manufacturer: String? = null,
    val ICAOTypeCode: String? = null,
    val Type: String? = null,
    val RegisteredOwners: String? = null,
    val OperatorFlagCode: String? = null
)
