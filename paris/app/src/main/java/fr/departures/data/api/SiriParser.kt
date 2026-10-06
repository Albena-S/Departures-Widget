package fr.departures.data.api

import fr.departures.data.AppJson
import fr.departures.data.Departure
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject
import java.time.Instant
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId

private val PARIS: ZoneId = ZoneId.of("Europe/Paris")

/**
 * Parses a PRIM stop-monitoring (SIRI Lite) JSON body.
 * Path: Siri.ServiceDelivery.StopMonitoringDelivery[*].MonitoredStopVisit[*].MonitoredVehicleJourney.
 * SIRI wraps many strings as {"value": "..."} or [{"value": "..."}]; both shapes are accepted.
 */
fun parseStopMonitoring(body: String): List<Departure> {
    val root = AppJson.parseToJsonElement(body).jsonObject
    val deliveries = root.obj("Siri")?.obj("ServiceDelivery")?.get("StopMonitoringDelivery").asList()
    return deliveries.flatMap { d -> (d as? JsonObject)?.get("MonitoredStopVisit").asList() }
        .mapNotNull { visit -> (visit as? JsonObject)?.obj("MonitoredVehicleJourney")?.let(::toDeparture) }
        .sortedBy { it.expected }
}

private fun toDeparture(j: JsonObject): Departure? {
    val call = j.obj("MonitoredCall") ?: return null
    val lineRef = j["LineRef"].text() ?: return null
    val destination = j["DestinationName"].text()
        ?: call["DestinationDisplay"].text()
        ?: j["DirectionName"].text()
        ?: ""
    val aimedDep = call["AimedDepartureTime"].text()?.let(::parseSiriTime)
    val aimedArr = call["AimedArrivalTime"].text()?.let(::parseSiriTime)
    val expected = call["ExpectedDepartureTime"].text()?.let(::parseSiriTime)
        ?: call["ExpectedArrivalTime"].text()?.let(::parseSiriTime)
        ?: aimedDep ?: aimedArr
        ?: return null
    val status = call["DepartureStatus"].text() ?: call["ArrivalStatus"].text()
    return Departure(
        lineRef = lineRef,
        destination = destination.trim(),
        expected = expected.toEpochMilli(),
        aimed = (aimedDep ?: aimedArr)?.toEpochMilli(),
        cancelled = status.equals("cancelled", ignoreCase = true),
        atStop = (call["VehicleAtStop"] as? JsonPrimitive)?.content?.toBooleanStrictOrNull() == true,
        platform = normalizePlatform(call["DeparturePlatformName"].text() ?: call["ArrivalPlatformName"].text()),
    )
}

/** "Voie 2" / "Quai A" / "2" → "2" / "A". Anything longer than 4 characters is not shown. */
fun normalizePlatform(raw: String?): String? {
    val s = raw?.trim()
        ?.replace(Regex("^(voie|quai|platform|track)\\s*", RegexOption.IGNORE_CASE), "")
        ?.trim()
        .orEmpty()
    return s.takeIf { it.isNotEmpty() && it.length <= 4 }
}

/** UTC/offset ISO times → Instant; an offset-less time is taken as Paris local time. */
fun parseSiriTime(s: String): Instant? = runCatching { OffsetDateTime.parse(s).toInstant() }
    .recoverCatching { Instant.parse(s) }
    .recoverCatching { LocalDateTime.parse(s).atZone(PARIS).toInstant() }
    .getOrNull()

private fun JsonObject.obj(key: String): JsonObject? = this[key] as? JsonObject

private fun JsonElement?.asList(): List<JsonElement> = when (this) {
    is JsonArray -> this
    null -> emptyList()
    else -> listOf(this)
}

/** "x", {"value":"x"}, [{"value":"x"}] → "x". */
private fun JsonElement?.text(): String? = when (this) {
    is JsonPrimitive -> if (isString) content.takeIf { it.isNotBlank() } else null
    is JsonObject -> this["value"].text()
    is JsonArray -> firstOrNull().text()
    else -> null
}
