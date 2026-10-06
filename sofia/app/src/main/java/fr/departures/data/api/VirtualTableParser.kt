package fr.departures.data.api

import fr.departures.data.AppJson
import fr.departures.data.Departure
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull

/** The body wasn't JSON (login page, maintenance HTML…): the session or the endpoint is broken. */
class NotJsonException(snippet: String) : Exception("Not JSON: ${snippet.take(120)}")

/** JSON with entries, none of which look like a line: the unofficial format has changed. */
class FormatChangedException(val snippet: String) : Exception("Unrecognised format: ${snippet.take(120)}")

/** One (line, destination) pair at a stop, as returned by the virtual table. */
data class VtRow(
    val extId: String,
    val name: String,
    val type: Int,
    val color: Int?,
    val lastStop: String,
    /** Latin destination (`st_name_en`), Cyrillic `st_name` when the Latin one is empty. */
    val destination: String,
    /** Whole minutes until arrival, as published. */
    val minutes: List<Int>,
)

/**
 * Parses `POST /bg/trip/getVirtualTable`. The body is an object keyed "<last_stop>_<ext_id>";
 * an empty object or array means no vehicles right now.
 */
fun parseVirtualTable(body: String): List<VtRow> {
    val root: JsonElement = try {
        AppJson.parseToJsonElement(body.trim())
    } catch (e: Exception) {
        throw NotJsonException(body)
    }
    val values = when (root) {
        is JsonObject -> root.values
        is JsonArray -> root
        else -> throw NotJsonException(body)
    }
    val rows = values.mapNotNull { v ->
        val o = v as? JsonObject ?: return@mapNotNull null
        val extId = o.str("ext_id") ?: return@mapNotNull null
        VtRow(
            extId = extId,
            name = o.str("name") ?: extId,
            type = (o["type"] as? JsonPrimitive)?.let { it.intOrNull ?: it.content.toIntOrNull() } ?: 0,
            color = parseHexColor(o.str("color")),
            lastStop = o.str("last_stop").orEmpty(),
            destination = o.str("st_name_en")?.takeIf { it.isNotBlank() } ?: o.str("st_name").orEmpty(),
            minutes = (o["details"] as? JsonArray).orEmpty().mapNotNull { d ->
                ((d as? JsonObject)?.get("t") as? JsonPrimitive)?.let { it.intOrNull ?: it.content.toIntOrNull() }
            },
        )
    }
    if (rows.isEmpty() && values.isNotEmpty()) throw FormatChangedException(body.take(300))
    return rows
}

/** Whole minutes → absolute times, so the widget can keep counting down between fetches. */
fun List<VtRow>.toDepartures(fetchedAt: Long): List<Departure> =
    flatMap { r ->
        r.minutes.map { t ->
            Departure(
                lineRef = r.extId,
                destination = r.destination,
                expected = fetchedAt + t * 60_000L,
                destinationId = r.lastStop,
            )
        }
    }.sortedBy { it.expected }

/** "#2AA9E0" → ARGB int (no android.graphics so it runs in JVM tests). */
fun parseHexColor(hex: String?): Int? {
    val h = hex?.trim()?.removePrefix("#") ?: return null
    if (h.length != 6) return null
    return h.toLongOrNull(16)?.let { (0xFF000000L or it).toInt() }
}

/** Any primitive as text (numbers included: a renamed type must not silently drop every row). */
private fun JsonObject.str(key: String): String? =
    (this[key] as? JsonPrimitive)?.takeIf { it !is kotlinx.serialization.json.JsonNull }?.content?.trim()
