package fr.departures

import fr.departures.data.Departure
import fr.departures.data.ErrorKind
import fr.departures.data.api.StationRow
import fr.departures.data.api.groupStationRows
import fr.departures.data.backoffMs
import fr.departures.data.filterForEntry
import fr.departures.data.intervalDue
import fr.departures.data.model.Entry
import fr.departures.data.model.Mode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FixesTest {

    // Review #1: interval is measured from the last ATTEMPT, so failures don't refetch every tick.
    @Test fun intervalMeasuredFromLastAttempt() {
        val now = 1_000_000L
        assertFalse(intervalDue(now, lastAttemptAt = now - 5_000, intervalMs = 30_000))
        assertTrue(intervalDue(now, lastAttemptAt = now - 30_000, intervalMs = 30_000))
        assertTrue(intervalDue(now, lastAttemptAt = null, intervalMs = 30_000))
    }

    @Test fun failuresBackOffExponentiallyWithCaps() {
        assertEquals(15_000L, backoffMs(ErrorKind.NETWORK, 0))
        assertEquals(30_000L, backoffMs(ErrorKind.NETWORK, 1))
        assertEquals(300_000L, backoffMs(ErrorKind.NETWORK, 20))
        assertEquals(30_000L, backoffMs(ErrorKind.RATE_LIMIT, 0))
        assertEquals(600_000L, backoffMs(ErrorKind.RATE_LIMIT, 20))
        assertEquals(60_000L, backoffMs(ErrorKind.AUTH, 0))
        assertEquals(1_800_000L, backoffMs(ErrorKind.AUTH, 20))
    }

    // Review minor (re-graded): a train that already left must not pulse "0".
    @Test fun departedTrainsAreDroppedUnlessAtStop() {
        val now = 10_000_000L
        val e = Entry(stopRef = "s", stopName = "S", lineRef = "STIF:Line::C01737:", lineShortName = "H", mode = Mode.TRAIN)
        val gone = Departure("STIF:Line::C01737:", "Paris Nord", now - 30_000)
        val atPlatform = Departure("STIF:Line::C01737:", "Paris Nord", now - 30_000, atStop = true)
        val next = Departure("STIF:Line::C01737:", "Paris Nord", now + 120_000)
        assertEquals(listOf(atPlatform, next), filterForEntry(listOf(gone, atPlatform, next), e, now))
    }

    // Review #2: rail stations rank above bus stops, and each hit lists its lines.
    @Test fun stationGroupingRanksRailAndListsLines() {
        val rows = listOf(
            StationRow("IDFM:5252", "Gare de Cernay", "Ermont", "Bus", "Soir"),
            StationRow("IDFM:monomodalStopPlace:43105", "Cernay", "Ermont", "LocalTrain", "H"),
            StationRow("IDFM:monomodalStopPlace:43105", "Cernay", "Ermont", "RapidTransit", "C"),
        )
        val hits = groupStationRows(rows, "Cernay")
        assertEquals("IDFM:monomodalStopPlace:43105", hits[0].stopId)
        assertEquals(listOf("C", "H"), hits[0].lines)
        assertEquals(setOf(Mode.RER, Mode.TRAIN), hits[0].modes)
        assertEquals(listOf("Soir"), hits[1].lines)
    }
}
