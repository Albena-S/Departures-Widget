package fr.departures

import fr.departures.data.Departure
import fr.departures.data.ErrorKind
import fr.departures.data.WidgetDataState
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

    private val now = Instant.parse("2026-10-06T10:00:00Z")

    private fun dep(atMs: Long, aimed: Long? = null, cancelled: Boolean = false, atStop: Boolean = false) =
        Departure("L", "D", atMs, aimed, cancelled, atStop)

    @Test fun formatsTimes() {
        val n = now.toEpochMilli()
        assertEquals("11 min", formatDeparture(dep(n + 11 * 60_000 + 59_000), now).text) // floored
        assertEquals("0", formatDeparture(dep(n + 30_000), now).text)
        assertEquals(CellStyle.PULSE, formatDeparture(dep(n + 5 * 60_000, atStop = true), now).style)
        assertEquals("59 min", formatDeparture(dep(n + 59 * 60_000 + 59_000), now).text)
        // 10:00Z = 13:00 Sofia (EEST); +60 min → 14:00
        assertEquals("14:00", formatDeparture(dep(n + 60 * 60_000), now).text)
        assertEquals(CellStyle.DELAYED, formatDeparture(dep(n + 8 * 60_000, aimed = n + 5 * 60_000), now).style)
        assertEquals(CellStyle.NORMAL, formatDeparture(dep(n + 8 * 60_000, aimed = n + 7 * 60_000 + 30_000), now).style)
        assertEquals(CellStyle.CANCELLED, formatDeparture(dep(n + 8 * 60_000, cancelled = true), now).style)
    }

    @Test fun acrossMidnight() {
        val paris = ZoneId.of("Europe/Sofia")
        val t = ZonedDateTime.of(2026, 10, 6, 23, 55, 0, 0, paris).toInstant()
        val d = dep(ZonedDateTime.of(2026, 10, 7, 0, 10, 0, 0, paris).toInstant().toEpochMilli())
        assertEquals("15 min", formatDeparture(d, t).text)
        // DST end night (25 Oct 2026, 04:00 EEST → 03:00 EET): 02:50 → 03:20 EET is 90 min later
        val before = ZonedDateTime.of(2026, 10, 25, 2, 50, 0, 0, paris).toInstant()
        val after = dep(before.toEpochMilli() + 90 * 60_000)
        assertEquals("03:20", formatDeparture(after, before).text)
    }

    @Test fun footer() {
        val n = now.toEpochMilli()
        assertEquals("Updated just now" to false, footerText(WidgetDataState(n - 3_000, null, false), now))
        assertEquals("Updated 12 s ago" to false, footerText(WidgetDataState(n - 12_000, null, false), now))
        assertEquals("Updated 4 min ago" to true, footerText(WidgetDataState(n - 4 * 60_000, null, false), now))
        assertEquals("Updated 30 s ago" to true, footerText(WidgetDataState(n - 30_000, ErrorKind.NETWORK, false), now))
        assertEquals("Sofia data unavailable" to true, footerText(WidgetDataState(n, ErrorKind.UNAVAILABLE, false), now))
    }

}
