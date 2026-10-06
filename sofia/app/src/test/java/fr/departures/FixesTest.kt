package fr.departures

import fr.departures.data.ErrorKind
import fr.departures.data.backoffMs
import fr.departures.data.intervalDue
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
        assertEquals(15_000L, backoffMs(ErrorKind.UNAVAILABLE, 0))
    }
}
