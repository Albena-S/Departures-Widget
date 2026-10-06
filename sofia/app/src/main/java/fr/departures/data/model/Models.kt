package fr.departures.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class Mode {
    METRO, RER, TRAIN, TRAM, BUS, TROLLEY, OTHER;

    companion object {
        /** sofiatraffic.bg virtual-table `type`: 1 bus, 2 tram, 3 metro, 4 trolleybus, 5 night bus. */
        fun fromSofiaType(type: Int): Mode = when (type) {
            1, 5 -> BUS
            2 -> TRAM
            3 -> METRO
            4 -> TROLLEY
            else -> OTHER
        }
    }
}

/** `Entry.lineRef` value meaning "any line at this stop" (offered when no vehicles are running at setup). */
const val ALL_LINES = "*"

/**
 * Sofia mapping of the shared fields: stopRef = stop code ("0328"), lineRef = line `ext_id` ("TB6")
 * or [ALL_LINES], lineShortName = rider-facing number ("6"), directions = `last_stop` ids.
 */
@Serializable
data class Entry(
    val id: Long = 0,
    val groupId: Long = 0,
    val sortOrder: Int = 0,
    val stopRef: String,
    val stopName: String,
    val lineRef: String,
    val lineShortName: String,
    val mode: Mode,
    val lineColor: Int? = null,
    val lineTextColor: Int? = null,
    /** Destination stop ids (`last_stop`) to keep; empty = all directions. */
    val directions: Set<String> = emptySet(),
    /** Destination id → Latin name, for showing saved directions in the editor. */
    val directionLabels: Map<String, String> = emptyMap(),
)

@Serializable
data class Group(
    val id: Long,
    val title: String,
    val sortOrder: Int,
    val entries: List<Entry> = emptyList(),
)
