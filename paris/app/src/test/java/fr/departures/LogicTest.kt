package fr.departures

import fr.departures.data.Departure
import fr.departures.data.ErrorKind
import fr.departures.data.WidgetDataState
import fr.departures.data.api.lineRefFor
import fr.departures.data.api.monitoringRefFor
import fr.departures.data.api.parseStopMonitoring
import fr.departures.data.filterForEntry
import fr.departures.data.model.Entry
import fr.departures.data.model.Mode
import fr.departures.widget.CellStyle
import fr.departures.widget.footerText
import fr.departures.widget.formatDeparture
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.Instant
import java.time.ZonedDateTime
import java.time.ZoneId

class LogicTest {

    private val sample = """
    {"Siri":{"ServiceDelivery":{"ResponseTimestamp":"2026-10-06T10:00:00.000Z","StopMonitoringDelivery":[{
      "ResponseTimestamp":"2026-10-06T10:00:00.000Z","Version":"2.0","Status":"true",
      "MonitoredStopVisit":[
        {"RecordedAtTime":"2026-10-06T09:59:00Z","MonitoringRef":{"value":"STIF:StopPoint:Q:43105:"},
         "MonitoredVehicleJourney":{"LineRef":{"value":"STIF:Line::C01727:"},
           "DirectionName":[{"value":"PARIS"}],"DestinationName":[{"value":"Paris Austerlitz"}],
           "MonitoredCall":{"StopPointName":[{"value":"Cernay"}],"VehicleAtStop":false,
             "AimedDepartureTime":"2026-10-06T10:05:00.000Z","ExpectedDepartureTime":"2026-10-06T10:08:00.000Z",
             "DepartureStatus":"delayed"}}},
        {"MonitoredVehicleJourney":{"LineRef":{"value":"STIF:Line::C01737:"},
           "DestinationName":[{"value":"Paris Nord"}],
           "MonitoredCall":{"VehicleAtStop":true,"ExpectedArrivalTime":"2026-10-06T10:00:30Z","DepartureStatus":"onTime"}}},
        {"MonitoredVehicleJourney":{"LineRef":{"value":"STIF:Line::C01737:"},
           "DestinationName":[{"value":"Paris Nord"}],
           "MonitoredCall":{"ExpectedDepartureTime":"2026-10-06T10:20:00Z","AimedDepartureTime":"2026-10-06T10:20:00Z","DepartureStatus":"cancelled"}}},
        {"MonitoredVehicleJourney":{"LineRef":{"value":"STIF:Line::C01737:"},
           "MonitoredCall":{"StopPointName":[{"value":"no time"}]}}}
      ]}]}}}
    """.trimIndent()

    private val now = Instant.parse("2026-10-06T10:00:00Z")

    @Test fun parsesSiriLite() {
        val list = parseStopMonitoring(sample)
        assertEquals(3, list.size)
        val first = list[0]
        assertEquals("STIF:Line::C01737:", first.lineRef)
        assertTrue(first.atStop)
        val rer = list.first { it.lineRef.contains("C01727") }
        assertEquals("Paris Austerlitz", rer.destination)
        assertEquals(Instant.parse("2026-10-06T10:08:00Z").toEpochMilli(), rer.expected)
        assertEquals(Instant.parse("2026-10-06T10:05:00Z").toEpochMilli(), rer.aimed)
        assertTrue(list.last().cancelled)
    }

    @Test fun parsesEmptyDelivery() {
        assertEquals(0, parseStopMonitoring("""{"Siri":{"ServiceDelivery":{"StopMonitoringDelivery":[{"MonitoredStopVisit":[]}]}}}""").size)
    }

    private fun entry(line: String, dirs: Set<String> = emptySet()) = Entry(
        stopRef = "STIF:StopArea:SP:43105:", stopName = "Cernay", lineRef = line,
        lineShortName = "H", mode = Mode.TRAIN, directions = dirs,
    )

    @Test fun filtersByLineAndDirection() {
        val list = parseStopMonitoring(sample)
        val h = filterForEntry(list, entry("STIF:Line::C01737:", setOf("Paris Nord")), now.toEpochMilli())
        assertEquals(2, h.size)
        assertTrue(h[1].cancelled) // cancelled still counts toward the 3 shown
        val none = filterForEntry(list, entry("STIF:Line::C01737:", setOf("Pontoise")), now.toEpochMilli())
        assertEquals(0, none.size)
        val rer = filterForEntry(list, entry("C01727"), now.toEpochMilli())
        assertEquals(1, rer.size)
    }

    private fun dep(atMs: Long, aimed: Long? = null, cancelled: Boolean = false, atStop: Boolean = false) =
        Departure("L", "D", atMs, aimed, cancelled, atStop)

    @Test fun formatsTimes() {
        val n = now.toEpochMilli()
        assertEquals("11 min", formatDeparture(dep(n + 11 * 60_000 + 59_000), now).text) // floored
        assertEquals("0", formatDeparture(dep(n + 30_000), now).text)
        assertEquals(CellStyle.PULSE, formatDeparture(dep(n + 5 * 60_000, atStop = true), now).style)
        assertEquals("59 min", formatDeparture(dep(n + 59 * 60_000 + 59_000), now).text)
        // 10:00Z = 12:00 Paris (CEST); +60 min → 13:00
        assertEquals("13:00", formatDeparture(dep(n + 60 * 60_000), now).text)
        assertEquals(CellStyle.DELAYED, formatDeparture(dep(n + 8 * 60_000, aimed = n + 5 * 60_000), now).style)
        assertEquals(CellStyle.NORMAL, formatDeparture(dep(n + 8 * 60_000, aimed = n + 7 * 60_000 + 30_000), now).style)
        assertEquals(CellStyle.CANCELLED, formatDeparture(dep(n + 8 * 60_000, cancelled = true), now).style)
    }

    @Test fun acrossMidnight() {
        val paris = ZoneId.of("Europe/Paris")
        val t = ZonedDateTime.of(2026, 10, 6, 23, 55, 0, 0, paris).toInstant()
        val d = dep(ZonedDateTime.of(2026, 10, 7, 0, 10, 0, 0, paris).toInstant().toEpochMilli())
        assertEquals("15 min", formatDeparture(d, t).text)
        // DST end night (25 Oct 2026, 03:00 CEST → 02:00 CET): 01:50 → 02:20 CET is 90 min later
        val before = ZonedDateTime.of(2026, 10, 25, 1, 50, 0, 0, paris).toInstant()
        val after = dep(before.toEpochMilli() + 90 * 60_000)
        assertEquals("02:20", formatDeparture(after, before).text)
    }

    @Test fun footer() {
        val n = now.toEpochMilli()
        assertEquals("Updated just now" to false, footerText(WidgetDataState(n - 3_000, null, false), now))
        assertEquals("Updated 12 s ago" to false, footerText(WidgetDataState(n - 12_000, null, false), now))
        assertEquals("Updated 4 min ago" to true, footerText(WidgetDataState(n - 4 * 60_000, null, false), now))
        assertEquals("Updated 30 s ago" to true, footerText(WidgetDataState(n - 30_000, ErrorKind.NETWORK, false), now))
        assertEquals("Rate limit, retrying soon" to true, footerText(WidgetDataState(n, ErrorKind.RATE_LIMIT, false), now))
    }

    @Test fun idMapping() {
        assertEquals("STIF:StopArea:SP:43105:", monitoringRefFor("IDFM:monomodalStopPlace:43105"))
        assertEquals("STIF:StopPoint:Q:5252:", monitoringRefFor("IDFM:5252"))
        assertEquals("STIF:Line::C01727:", lineRefFor("IDFM:C01727"))
    }
}
