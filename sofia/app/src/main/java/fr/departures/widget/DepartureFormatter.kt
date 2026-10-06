package fr.departures.widget

import fr.departures.data.Departure
import fr.departures.data.ErrorKind
import fr.departures.data.WidgetDataState
import kotlinx.serialization.Serializable
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@Serializable
enum class CellStyle { NORMAL, DELAYED, CANCELLED, PULSE, EMPTY }

@Serializable
data class TimeCell(val text: String, val style: CellStyle, val platform: String? = null)

private val PARIS = ZoneId.of("Europe/Sofia") // name kept from the Paris app
private val HHMM = DateTimeFormatter.ofPattern("HH:mm")

const val STALE_AFTER_MS = 120_000L

/** SPEC §6.2. Minutes are floored and always computed from absolute times. */
fun formatDeparture(d: Departure, now: Instant): TimeCell {
    val deltaMs = d.expected - now.toEpochMilli()
    val mins = Math.floorDiv(deltaMs, 60_000L)
    val text = when {
        d.atStop || mins < 1 -> "0"
        mins < 60 -> "$mins min"
        else -> HHMM.format(Instant.ofEpochMilli(d.expected).atZone(PARIS))
    }
    val delayed = d.aimed != null && d.expected - d.aimed >= 60_000L
    val style = when {
        d.cancelled -> CellStyle.CANCELLED
        text == "0" -> CellStyle.PULSE
        delayed -> CellStyle.DELAYED
        else -> CellStyle.NORMAL
    }
    return TimeCell(text, style, d.platform)
}

fun emptyCell() = TimeCell("—", CellStyle.EMPTY)

/** Footer text and whether it should use the warning colour. */
fun footerText(state: WidgetDataState, now: Instant): Pair<String, Boolean> {
    if (state.error == ErrorKind.RATE_LIMIT) return "Rate limit, retrying soon" to true
    if (state.error == ErrorKind.UNAVAILABLE) return "Sofia data unavailable" to true
    val fetched = state.oldestFetchedAt ?: return (if (state.fetching) "Loading…" else "Not updated yet") to (state.error != null)
    val ageS = ((now.toEpochMilli() - fetched) / 1000).coerceAtLeast(0)
    val text = when {
        ageS < 10 -> "Updated just now"
        ageS < 60 -> "Updated $ageS s ago"
        ageS < 3600 -> "Updated ${ageS / 60} min ago"
        else -> "Updated ${HHMM.format(Instant.ofEpochMilli(fetched).atZone(PARIS))}"
    }
    return text to (ageS * 1000 > STALE_AFTER_MS || state.error != null)
}
