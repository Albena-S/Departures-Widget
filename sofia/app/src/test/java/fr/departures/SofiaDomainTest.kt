package fr.departures

import fr.departures.data.Departure
import fr.departures.data.filterForEntry
import fr.departures.data.model.ALL_LINES
import fr.departures.data.model.Entry
import fr.departures.data.model.Mode
import org.junit.Assert.assertEquals
import org.junit.Test

class SofiaDomainTest {
    private val now = 10_000_000L
    private fun dep(line: String, dest: String, inMs: Long) = Departure(line, "Name $dest", now + inMs, destinationId = dest)
    private fun entry(line: String, dirs: Set<String> = emptySet()) = Entry(
        stopRef = "0328", stopName = "БУЛ. К. ВЕЛИЧКОВ", lineRef = line, lineShortName = "6", mode = Mode.TROLLEY, directions = dirs,
    )

    @Test fun filtersByLineAndDestinationId() {
        val all = listOf(dep("TB6", "TB0648", 60_000), dep("TB6", "TB9999", 120_000), dep("TB7", "TB2171", 30_000))
        assertEquals(listOf(all[0]), filterForEntry(all, entry("TB6", setOf("TB0648")), now))
        assertEquals(2, filterForEntry(all, entry("TB6"), now).size)
    }

    @Test fun allLinesEntryMatchesEveryLine() {
        val all = listOf(dep("TB6", "a", 60_000), dep("A60", "b", 120_000), dep("TB7", "c", 30_000), dep("TM5", "d", 10_000))
        val got = filterForEntry(all, entry(ALL_LINES), now)
        assertEquals(listOf("TM5", "TB7", "TB6"), got.map { it.lineRef })
    }

    // Review focus 4: whole-minute data; a "0" must not vanish seconds after the fetch.
    @Test fun arrivingNowIsKeptForAMinute() {
        val arriving = dep("TB6", "x", 0)
        assertEquals(1, filterForEntry(listOf(arriving), entry("TB6"), now + 59_000).size)
        assertEquals(0, filterForEntry(listOf(arriving), entry("TB6"), now + 61_000).size)
    }

    @Test fun mapsSofiaVehicleTypes() {
        assertEquals(Mode.BUS, Mode.fromSofiaType(1))
        assertEquals(Mode.TRAM, Mode.fromSofiaType(2))
        assertEquals(Mode.METRO, Mode.fromSofiaType(3))
        assertEquals(Mode.TROLLEY, Mode.fromSofiaType(4))
        assertEquals(Mode.BUS, Mode.fromSofiaType(5))
        assertEquals(Mode.OTHER, Mode.fromSofiaType(42))
    }
}
