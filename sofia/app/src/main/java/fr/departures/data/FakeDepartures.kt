package fr.departures.data

import fr.departures.data.model.ALL_LINES
import fr.departures.data.model.Group

/**
 * Debug-only synthetic data (toggle in Settings, debug builds) so every Sofia display state can be
 * screenshotted: arriving now (pulsing 0), minutes, >= 60 min clock time, and an empty row.
 */
object FakeDepartures {
    private const val MIN = 60_000L

    fun generate(groups: List<Group>, now: Long): Map<String, List<Departure>> {
        val out = mutableMapOf<String, MutableList<Departure>>()
        val entries = groups.flatMap { it.entries }.distinctBy { it.stopRef to it.lineRef }
        entries.forEachIndexed { i, e ->
            val line = if (e.lineRef == ALL_LINES) "TB6" else e.lineRef
            val destId = e.directions.firstOrNull() ?: "FAKE"
            fun d(inMin: Int) = Departure(
                lineRef = line, destination = e.directionLabels[destId] ?: "Fake destination",
                expected = now + inMin * MIN, destinationId = destId,
            )
            val list = when (i % 3) {
                0 -> listOf(d(0), d(7), d(75))
                1 -> listOf(d(3), d(12), d(26))
                else -> emptyList()
            }
            out.getOrPut(e.stopRef) { mutableListOf() } += list
        }
        return out
    }
}
