package fr.departures

import fr.departures.data.model.Mode
import fr.departures.data.stops.StopIndex
import fr.departures.data.stops.normalize
import fr.departures.data.stops.parseStopsCsv
import fr.departures.data.stops.toLatin
import org.junit.Assert.assertEquals
import org.junit.Test

class StopsTest {
    private val csv = """stop_id,stop_code,stop_name,stop_desc,stop_lat,stop_lon,location_type,parent_station,stop_timezone,level_id
A0328,0328,БУЛ. К. ВЕЛИЧКОВ,,42.70,23.29,0,,,
TB0328,0328,БУЛ. К. ВЕЛИЧКОВ,,42.70,23.29,0,,,
A0329,0329,БУЛ. К. ВЕЛИЧКОВ,,42.71,23.30,0,,,
TM0720,0720,НУ ЗА ТАНЦОВО ИЗКУСТВО,,42.6,23.3,0,,,
M18,18,МЛАДОСТ 3,,42.6,23.3,0,,,
A0018,0018,QУЛ. QQОБОРИЩЕQQQ,,42.6,23.3,0,,,
MSt18,,МЛАДОСТ 3,,42.6,23.3,1,,,
A9000,9000,ЦЕНТРАЛНА ГАРА,,42.6,23.3,0,,,
A9001,9001,ГАРА СОФИЯ СЕВЕР,,42.6,23.3,0,,,
""".replace('Q', '"') // Q stands for a CSV quote (a raw string can't hold doubled quotes)

    @Test fun transliteratesWithTheOfficialScheme() {
        assertEquals("bul. k. velichkov", toLatin("БУЛ. К. ВЕЛИЧКОВ"))
        assertEquals("sofia", toLatin("СОФИЯ"))
        assertEquals("shtastie yuzhen", toLatin("ЩАСТИЕ ЮЖЕН"))
        assertEquals("zh.k. mladost", toLatin("Ж.К. МЛАДОСТ"))
        assertEquals("bul k velichkov", normalize("БУЛ. К. ВЕЛИЧКОВ"))
        assertEquals("ul oborishte", normalize(toLatin("УЛ. \"ОБОРИЩЕ\"")))
    }

    @Test fun dedupesByCodeAndCollectsModes() {
        val stops = parseStopsCsv(csv)
        val s = stops.single { it.code == "0328" }
        assertEquals(setOf(Mode.BUS, Mode.TROLLEY), s.modes)
        assertEquals("bul. k. velichkov", s.latin)
        assertEquals(7, stops.size) // MSt18 has no code and location_type 1
        assertEquals(setOf(Mode.METRO), stops.single { it.code == "18" }.modes)
        assertEquals("УЛ. \"ОБОРИЩЕ\"", stops.single { it.code == "0018" }.name)
    }

    @Test fun searchesByCodeCyrillicAndLatin() {
        val idx = StopIndex(parseStopsCsv(csv))
        assertEquals(listOf("0328"), idx.search("0328").map { it.code })
        // Review focus 3: short metro codes match exactly, not zero-padded.
        assertEquals(listOf("18"), idx.search("18").map { it.code })
        assertEquals(listOf("0720"), idx.search("720").map { it.code }) // padded fallback when no exact code
        assertEquals(setOf("0328", "0329"), idx.search("velichkov").map { it.code }.toSet())
        assertEquals(setOf("0328", "0329"), idx.search("величков").map { it.code }.toSet())
        assertEquals(setOf("0328", "0329"), idx.search("bul k vel").map { it.code }.toSet())
        assertEquals(listOf("0018"), idx.search("oborishte").map { it.code })
        // name starting with the query ranks before a later word match
        assertEquals(listOf("9001", "9000"), idx.search("gara").map { it.code })
        assertEquals(0, idx.search("x").size)
    }
}
