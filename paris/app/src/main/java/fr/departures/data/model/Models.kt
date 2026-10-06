package fr.departures.data.model

import kotlinx.serialization.Serializable

@Serializable
enum class Mode {
    METRO, RER, TRAIN, TRAM, BUS, OTHER;

    companion object {
        /** Maps `arrets-lignes.mode` (or `referentiel-des-lignes.transportmode`) to an app mode. */
        fun fromDataset(mode: String?): Mode = when (mode?.trim()?.lowercase()) {
            "metro" -> METRO
            "rapidtransit" -> RER
            "localtrain", "regionalrail", "railshuttle", "rail" -> TRAIN
            "tramway", "tram" -> TRAM
            "bus" -> BUS
            else -> OTHER
        }
    }
}

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
    /** Destination names to keep; empty = all directions. */
    val directions: Set<String> = emptySet(),
)

@Serializable
data class Group(
    val id: Long,
    val title: String,
    val sortOrder: Int,
    val entries: List<Entry> = emptyList(),
)

/** Bare IDFM line code (e.g. `C01727`) from any of the id formats in use. */
fun lineCode(ref: String): String = Regex("C\\d{5}").find(ref)?.value ?: ref
