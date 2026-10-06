package fr.departures

import fr.departures.data.Departure
import fr.departures.data.api.normalizePlatform
import fr.departures.data.api.parseStopMonitoring
import fr.departures.widget.CellStyle
import fr.departures.widget.TimeCell
import fr.departures.widget.fitRow
import fr.departures.widget.formatDeparture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.Instant

class PlatformTest {
    private val body = """
    {"Siri":{"ServiceDelivery":{"StopMonitoringDelivery":[{"MonitoredStopVisit":[
      {"MonitoredVehicleJourney":{"LineRef":{"value":"STIF:Line::C01737:"},"DestinationName":[{"value":"Paris Nord"}],
        "MonitoredCall":{"ExpectedDepartureTime":"2026-10-06T10:05:00Z","DeparturePlatformName":{"value":"2"}}}},
      {"MonitoredVehicleJourney":{"LineRef":{"value":"STIF:Line::C01737:"},"DestinationName":[{"value":"Paris Nord"}],
        "MonitoredCall":{"ExpectedDepartureTime":"2026-10-06T10:20:00Z","ArrivalPlatformName":{"value":"Voie B"}}}},
      {"MonitoredVehicleJourney":{"LineRef":{"value":"STIF:Line::C01737:"},"DestinationName":[{"value":"Paris Nord"}],
        "MonitoredCall":{"ExpectedDepartureTime":"2026-10-06T10:35:00Z"}}}
    ]}]}}}
    """.trimIndent()

    @Test fun parsesDepartureThenArrivalPlatform() {
        val list = parseStopMonitoring(body)
        assertEquals("2", list[0].platform)
        assertEquals("B", list[1].platform)
        assertNull(list[2].platform)
    }

    @Test fun normalizesPlatformNames() {
        assertEquals("2", normalizePlatform("Voie 2"))
        assertEquals("A", normalizePlatform(" quai A "))
        assertEquals("12", normalizePlatform("12"))
        assertNull(normalizePlatform("   "))
        assertNull(normalizePlatform("Platform unknown long name"))
    }

    @Test fun platformIsCarriedIntoTheCell() {
        val now = Instant.parse("2026-10-06T10:00:00Z")
        val d = Departure("L", "D", now.toEpochMilli() + 5 * 60_000, platform = "2")
        assertEquals("2", formatDeparture(d, now).platform)
    }

    @Test fun platformBoxTakesRoomFromLaterTimes() {
        val later = listOf(TimeCell("49 min", CellStyle.NORMAL), TimeCell("13:09", CellStyle.NORMAL))
        val without = fitRow(170f, 22f, TimeCell("20 min", CellStyle.NORMAL), later, null, compact = false)
        val with = fitRow(170f, 22f, TimeCell("20 min", CellStyle.NORMAL, platform = "2"), later, null, compact = false)
        assertEquals(2, without.laterCount)
        assertEquals(1, with.laterCount)
    }
}
