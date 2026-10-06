package fr.departures.data

import fr.departures.data.model.Group
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

/**
 * Debug-only synthetic data (toggle in Settings, debug builds) so every display state
 * can be screenshotted: at platform, delayed, cancelled, >= 60 min, empty, and a
 * departure just after midnight.
 */
object FakeDepartures {
    private const val MIN = 60_000L

    fun generate(groups: List<Group>, now: Long): Map<String, List<Departure>> {
        val out = mutableMapOf<String, MutableList<Departure>>()
        val entries = groups.flatMap { it.entries }.distinctBy { it.stopRef to it.lineRef }
        entries.forEachIndexed { i, e ->
            val dest = e.directions.firstOrNull() ?: "Paris Saint-Lazare"
            fun d(inMin: Double, delayMin: Int = 0, cancelled: Boolean = false, atStop: Boolean = false, platform: String? = listOf("1", "2", "B", null)[i % 4]) = Departure(
                lineRef = e.lineRef, destination = dest,
                expected = now + (inMin * MIN).toLong(),
                aimed = now + (inMin * MIN).toLong() - delayMin * MIN,
                cancelled = cancelled, atStop = atStop, platform = platform,
            )
            val list = when (i % 4) {
                0 -> listOf(d(0.3, atStop = true), d(4.5, delayMin = 3), d(75.0))
                1 -> listOf(d(2.2, cancelled = true), d(12.0), d(minutesUntilJustAfterMidnight(now)))
                2 -> emptyList()
                else -> listOf(d(7.0, delayMin = 2), d(19.0), d(33.0, cancelled = true))
            }
            out.getOrPut(e.stopRef) { mutableListOf() } += list
        }
        return out
    }

    /** Minutes until 00:10 Paris time if that is within 45 min, else 41 (keeps the row realistic). */
    private fun minutesUntilJustAfterMidnight(now: Long): Double {
        val t = Instant.ofEpochMilli(now).atZone(ZoneId.of("Europe/Paris"))
        val target = t.toLocalDate().plusDays(if (t.toLocalTime() > LocalTime.of(0, 10)) 1 else 0)
            .atTime(0, 10).atZone(t.zone)
        val mins = (target.toInstant().toEpochMilli() - now) / MIN.toDouble()
        return if (mins in 0.0..45.0) mins else 41.0
    }
}
