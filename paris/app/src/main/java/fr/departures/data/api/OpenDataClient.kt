package fr.departures.data.api

import android.graphics.Color
import fr.departures.data.AppJson
import fr.departures.data.model.Mode
import fr.departures.data.model.lineCode
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonObject

data class StationHit(val stopId: String, val name: String, val commune: String, val modes: Set<Mode>, val lines: List<String> = emptyList())

/** One `arrets-lignes` row (a stop × line pair). */
data class StationRow(val stopId: String, val name: String, val commune: String, val mode: String, val line: String)

/** Groups stop×line rows into stations: rail/metro/tram before bus-only, name-prefix matches first. */
fun groupStationRows(rows: List<StationRow>, query: String): List<StationHit> =
    rows.filter { it.stopId.isNotEmpty() }
        .groupBy { it.stopId }
        .map { (id, rs) ->
            StationHit(
                stopId = id,
                name = rs.first().name,
                commune = rs.first().commune,
                modes = rs.map { Mode.fromDataset(it.mode) }.toSet(),
                lines = rs.map { it.line }.filter { it.isNotBlank() }.distinct().sortedWith(compareBy({ it.length }, { it })),
            )
        }
        .sortedWith(compareBy<StationHit>({ it.modes == setOf(Mode.BUS) }, { !it.name.startsWith(query.trim(), true) }, { it.name }))

data class LineAtStation(
    val lineId: String,
    val shortName: String,
    val longName: String,
    val mode: Mode,
    val color: Int?,
    val textColor: Int?,
)

/** IDFM open data (Opendatasoft Explore v2.1), used only by the setup screens. */
class OpenDataClient(private val http: HttpClient) {

    suspend fun searchStations(q: String): List<StationHit> {
        val query = q.trim().replace("\"", "")
        if (query.length < 2) return emptyList()
        // Two queries so bus rows (thousands) can't crowd rail stations out of the 100-row page.
        val search = "search(stop_name, \"$query\")"
        val select = "stop_id,stop_name,nom_commune,mode,shortname"
        val rail = records("arrets-lignes", where = "$search and mode != \"Bus\"", select = select)
        val bus = records("arrets-lignes", where = "$search and mode = \"Bus\"", select = select)
        val rows = (rail + bus).map {
            StationRow(it.str("stop_id"), it.str("stop_name"), it.str("nom_commune"), it.str("mode"), it.str("shortname"))
        }
        return groupStationRows(rows, query)
    }

    suspend fun linesAt(stopId: String): List<LineAtStation> {
        val rows = records(
            "arrets-lignes",
            where = "stop_id = \"${stopId.replace("\"", "")}\"",
            select = "id,shortname,route_long_name,mode",
        )
        val lines = rows.distinctBy { lineCode(it.str("id")) }
        if (lines.isEmpty()) return emptyList()
        val codes = lines.map { lineCode(it.str("id")) }
        val meta = records(
            "referentiel-des-lignes",
            where = "id_line in (${codes.joinToString(",") { "\"$it\"" }})",
            select = "id_line,shortname_line,name_line,colourweb_hexa,textcolourweb_hexa",
        ).associateBy { it.str("id_line") }
        return lines.map { r ->
            val code = lineCode(r.str("id"))
            val m = meta[code]
            LineAtStation(
                lineId = code,
                shortName = m?.str("shortname_line")?.ifEmpty { null } ?: r.str("shortname"),
                longName = m?.str("name_line")?.ifEmpty { null } ?: r.str("route_long_name"),
                mode = Mode.fromDataset(r.str("mode")),
                color = parseHex(m?.str("colourweb_hexa")),
                textColor = parseHex(m?.str("textcolourweb_hexa")),
            )
        }.sortedWith(compareBy({ it.mode.ordinal }, { it.shortName.padStart(6, '0') }))
    }

    private suspend fun records(dataset: String, where: String, select: String): List<JsonObject> {
        val body = http.get("$BASE/$dataset/records") {
            parameter("where", where)
            parameter("select", select)
            parameter("limit", 100)
        }.bodyAsText()
        val root = AppJson.parseToJsonElement(body).jsonObject
        root["error_code"]?.let { throw IllegalStateException("Open data error: ${root["message"]}") }
        return (root["results"] as? JsonArray)?.mapNotNull { it as? JsonObject }.orEmpty()
    }

    companion object {
        const val BASE = "https://data.iledefrance-mobilites.fr/api/explore/v2.1/catalog/datasets"
    }
}

private fun JsonObject.str(key: String): String = (this[key] as? JsonPrimitive)?.takeIf { it.isString }?.content.orEmpty()

private fun parseHex(hex: String?): Int? {
    val h = hex?.trim()?.removePrefix("#") ?: return null
    if (h.length != 6) return null
    return runCatching { Color.parseColor("#$h") }.getOrNull()
}

/** `IDFM:monomodalStopPlace:43105` → `STIF:StopArea:SP:43105:`, `IDFM:5252` → `STIF:StopPoint:Q:5252:`. */
fun monitoringRefFor(stopId: String): String {
    val id = stopId.substringAfterLast(':')
    return if (stopId.contains("StopPlace", ignoreCase = true) || stopId.contains("StopArea", ignoreCase = true))
        "STIF:StopArea:SP:$id:" else "STIF:StopPoint:Q:$id:"
}

/** `C01727` → `STIF:Line::C01727:` */
fun lineRefFor(lineId: String): String = "STIF:Line::${lineCode(lineId)}:"
