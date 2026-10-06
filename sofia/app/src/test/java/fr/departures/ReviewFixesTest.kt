package fr.departures

import fr.departures.data.ErrorKind
import fr.departures.data.StopOutcome
import fr.departures.data.api.FormatChangedException
import fr.departures.data.api.SofiaClient
import fr.departures.data.api.SofiaResult
import fr.departures.data.api.parseVirtualTable
import fr.departures.data.mergeEntry
import fr.departures.data.model.Entry
import fr.departures.data.model.Mode
import fr.departures.data.runError
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReviewFixesTest {
    // I2: one broken stop must not blank the others.
    @Test fun unavailableOnlyWhenNothingSucceeded() {
        assertNull(runError(listOf(StopOutcome.OK, StopOutcome.UNAVAILABLE, StopOutcome.OK)).takeIf { it == ErrorKind.UNAVAILABLE })
        assertEquals(ErrorKind.NETWORK, runError(listOf(StopOutcome.OK, StopOutcome.UNAVAILABLE)))
        assertEquals(ErrorKind.UNAVAILABLE, runError(listOf(StopOutcome.UNAVAILABLE, StopOutcome.UNAVAILABLE)))
        assertEquals(ErrorKind.RATE_LIMIT, runError(listOf(StopOutcome.OK, StopOutcome.RATE_LIMITED)))
        assertNull(runError(listOf(StopOutcome.OK, StopOutcome.OK)))
    }

    // I3: a changed format must be noticed, not shown as empty rows.
    @Test fun numericExtIdStillParses() {
        val rows = parseVirtualTable("""{"x":{"name":7,"ext_id":42,"type":"4","last_stop":"TB1","details":[{"t":"3"}]}}""")
        assertEquals("42", rows.single().extId)
        assertEquals("7", rows.single().name)
        assertEquals(listOf(3), rows.single().minutes)
    }

    @Test fun entriesThatDontParseMeanTheFormatChanged() {
        val e = runCatching { parseVirtualTable("""{"a":{"line":"7","eta":[1,2]},"b":{"line":"9"}}""") }.exceptionOrNull()
        assertTrue(e is FormatChangedException)
    }

    @Test fun formatChangeIsUnavailableWithoutAHandshakeRetry() = runBlocking {
        var posts = 0
        val engine = MockEngine { req ->
            if (req.method == HttpMethod.Get) respond("", HttpStatusCode.OK, headersOf(HttpHeaders.SetCookie, "XSRF-TOKEN=t; path=/"))
            else { posts++; respond("""{"a":{"line":"7"}}""", HttpStatusCode.OK, headersOf(HttpHeaders.ContentType, "application/json")) }
        }
        val r = SofiaClient(engine, AcceptAllCookiesStorage()).virtualTable("0328")
        assertTrue(r is SofiaResult.Unavailable)
        assertEquals(1, posts)
    }

    // Minor 8 (fixed): 429 respects the rate-limit backoff.
    @Test fun tooManyRequestsIsRateLimited() = runBlocking {
        val engine = MockEngine { req ->
            if (req.method == HttpMethod.Get) respond("", HttpStatusCode.OK, headersOf(HttpHeaders.SetCookie, "XSRF-TOKEN=t; path=/"))
            else respond("slow down", HttpStatusCode.TooManyRequests)
        }
        assertTrue(SofiaClient(engine, AcceptAllCookiesStorage()).virtualTable("0328") is SofiaResult.RateLimited)
    }

    // Minor 7 (fixed): re-adding a line keeps direction names for the editor.
    @Test fun mergingEntriesKeepsDirectionLabels() {
        val a = Entry(stopRef = "0328", stopName = "S", lineRef = "TB6", lineShortName = "6", mode = Mode.TROLLEY,
            directions = setOf("TB0648"), directionLabels = mapOf("TB0648" to "ZH.K. LOZENETS"))
        val b = a.copy(directions = setOf("TB9"), directionLabels = mapOf("TB9" to "CENTER"))
        val m = mergeEntry(a, b)
        assertEquals(setOf("TB0648", "TB9"), m.directions)
        assertEquals(mapOf("TB0648" to "ZH.K. LOZENETS", "TB9" to "CENTER"), m.directionLabels)
        assertEquals(emptySet<String>(), mergeEntry(a, b.copy(directions = emptySet())).directions) // "all" wins
    }
}
