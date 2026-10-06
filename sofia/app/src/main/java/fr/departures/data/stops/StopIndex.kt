package fr.departures.data.stops

import fr.departures.data.model.Mode
import kotlinx.serialization.Serializable

/** One physical stop, keyed by the code printed on the sign. Bus/trolley/tram stop ids sharing a code are merged. */
@Serializable
data class SofiaStop(val code: String, val name: String, val latin: String, val modes: Set<Mode>)

/** GTFS `stops.txt` → stops with a code (location_type 0), one per code. */
fun parseStopsCsv(csv: String): List<SofiaStop> {
    val lines = csvRows(csv.removePrefix("\uFEFF"))
    if (lines.isEmpty()) return emptyList()
    val header = lines.first()
    fun col(name: String) = header.indexOf(name)
    val iId = col("stop_id"); val iCode = col("stop_code"); val iName = col("stop_name"); val iType = col("location_type")
    val byCode = LinkedHashMap<String, Pair<String, MutableSet<Mode>>>()
    for (r in lines.drop(1)) {
        val code = r.getOrNull(iCode)?.trim().orEmpty()
        val type = r.getOrNull(iType)?.trim().orEmpty()
        if (code.isEmpty() || (type.isNotEmpty() && type != "0")) continue
        val name = r.getOrNull(iName)?.trim().orEmpty()
        val entry = byCode.getOrPut(code) { name to mutableSetOf() }
        entry.second += modeForStopId(r.getOrNull(iId).orEmpty())
    }
    return byCode.map { (code, v) -> SofiaStop(code, v.first, toLatin(v.first), v.second - Mode.OTHER) }
}

/** stop_id prefix: A bus, TB trolleybus, TM tram, M/ME/MSt metro. */
private fun modeForStopId(id: String): Mode = when (id.takeWhile { it.isLetter() }) {
    "A" -> Mode.BUS
    "TB" -> Mode.TROLLEY
    "TM" -> Mode.TRAM
    "M", "ME", "MSt" -> Mode.METRO
    else -> Mode.OTHER
}

/** Minimal RFC 4180 reader (quoted fields, doubled quotes). */
private fun csvRows(text: String): List<List<String>> {
    val rows = mutableListOf<List<String>>()
    var row = mutableListOf<String>()
    val field = StringBuilder()
    var quoted = false
    var i = 0
    while (i < text.length) {
        val c = text[i]
        when {
            quoted && c == '"' && i + 1 < text.length && text[i + 1] == '"' -> { field.append('"'); i++ }
            c == '"' -> quoted = !quoted
            !quoted && c == ',' -> { row += field.toString(); field.clear() }
            !quoted && (c == '\n' || c == '\r') -> {
                if (c == '\r' && i + 1 < text.length && text[i + 1] == '\n') i++
                row += field.toString(); field.clear()
                if (row.any { it.isNotEmpty() }) rows += row
                row = mutableListOf()
            }
            else -> field.append(c)
        }
        i++
    }
    if (field.isNotEmpty() || row.isNotEmpty()) { row += field.toString(); rows += row }
    return rows
}

/** In-memory search over ~3,600 stops: fast enough to run on every keystroke. */
class StopIndex(val stops: List<SofiaStop>) {
    private val normalized = stops.map { normalize(it.latin) }
    private val byCode = stops.associateBy { it.code }

    fun search(q: String, limit: Int = 50): List<SofiaStop> {
        val query = q.trim()
        if (query.isEmpty()) return emptyList()
        if (query.all { it.isDigit() }) {
            // Exact code first ("18" is metro stop 18, not 0018); pad to 4 digits only as a fallback.
            byCode[query]?.let { return listOf(it) }
            byCode[query.padStart(4, '0')]?.let { return listOf(it) }
        }
        val nq = normalize(query)
        if (nq.length < 2) return emptyList()
        val qWords = nq.split(' ')
        val ranked = stops.indices.mapNotNull { i ->
            val name = normalized[i]
            val rank = when {
                name.startsWith(nq) -> 0
                wordsPrefix(name.split(' '), qWords) -> 1
                name.contains(nq) -> 2
                else -> return@mapNotNull null
            }
            Triple(rank, name, stops[i])
        }
        return ranked.sortedWith(compareBy({ it.first }, { it.second }, { it.third.code })).take(limit).map { it.third }
    }

    /** Each query word is a prefix of a later name word, in order ("bul k vel" ~ "bul k velichkov"). */
    private fun wordsPrefix(name: List<String>, q: List<String>): Boolean {
        var j = 0
        for (w in name) {
            if (j < q.size && w.startsWith(q[j])) j++
        }
        return j == q.size
    }
}
