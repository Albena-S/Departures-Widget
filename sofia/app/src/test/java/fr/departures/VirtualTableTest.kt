package fr.departures

import fr.departures.data.api.NotJsonException
import fr.departures.data.api.parseVirtualTable
import fr.departures.data.api.toDepartures
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

const val VT_SAMPLE = """{
 "TB2171_TB7":{"name":"7","ext_id":"TB7","type":4,"color":"#2AA9E0","st_name":"Ж.К. ГОЦЕ ДЕЛЧЕВ","st_name_en":"ZH.K. GOTSE DELTCHEV","last_stop":"TB2171",
   "details":[{"t":2,"ac":true,"wheelchairs":true,"bikes":false},{"t":23,"ac":true,"wheelchairs":true,"bikes":false}]},
 "A1311_A60":{"name":"310","ext_id":"A60","type":1,"color":"#BD202E","st_name":"ПЛ. СТОЧНА ГАРА","st_name_en":"","last_stop":"A1311",
   "details":[{"t":0},{"t":11}]}
}"""

class VirtualTableTest {
    @Test fun parsesRowsAndConvertsMinutesToAbsoluteTimes() {
        val rows = parseVirtualTable(VT_SAMPLE)
        assertEquals(2, rows.size)
        val tb7 = rows.first { it.extId == "TB7" }
        assertEquals("7", tb7.name)
        assertEquals(4, tb7.type)
        assertEquals(0xFF2AA9E0.toInt(), tb7.color)
        assertEquals("ZH.K. GOTSE DELTCHEV", tb7.destination)
        assertEquals("TB2171", tb7.lastStop)
        assertEquals(listOf(2, 23), tb7.minutes)
        // empty Latin name falls back to Cyrillic
        assertEquals("ПЛ. СТОЧНА ГАРА", rows.first { it.extId == "A60" }.destination)

        val deps = rows.toDepartures(fetchedAt = 1_000_000L)
        val first = deps.first { it.lineRef == "TB7" }
        assertEquals(1_000_000L + 2 * 60_000, first.expected)
        assertEquals("TB2171", first.destinationId)
        assertEquals(4, deps.size)
    }

    // Review focus 2: night / no vehicles can come back as an empty array or object.
    @Test fun emptyArrayOrObjectMeansNoVehicles() {
        assertEquals(0, parseVirtualTable("[]").size)
        assertEquals(0, parseVirtualTable("{}").size)
    }

    @Test fun htmlIsNotJson() {
        val e = runCatching { parseVirtualTable("<!DOCTYPE html><html>login</html>") }.exceptionOrNull()
        assertTrue(e is NotJsonException)
    }
}
