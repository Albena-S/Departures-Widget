package fr.departures

import fr.departures.data.api.SofiaClient
import fr.departures.data.api.SofiaResult
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respond
import io.ktor.client.plugins.cookies.AcceptAllCookiesStorage
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SofiaClientTest {
    private val json = headersOf(HttpHeaders.ContentType, "application/json")

    private class Log { val calls = mutableListOf<String>() }

    /** [posts] answers POSTs in order: status + body + optional rotated token. */
    private fun client(log: Log, posts: List<Triple<Int, String, String?>>): SofiaClient {
        var i = 0
        var handshakes = 0
        val engine = MockEngine { req ->
            if (req.method == HttpMethod.Get) {
                handshakes++
                log.calls += "GET"
                respond("", HttpStatusCode.OK, headersOf(HttpHeaders.SetCookie to listOf(
                    "XSRF-TOKEN=hs$handshakes%3D; path=/",
                    "sofia_traffic_session=s$handshakes; path=/; httponly",
                )))
            } else {
                log.calls += "POST token=${req.headers["X-XSRF-TOKEN"]}"
                val (status, body, rotated) = posts[minOf(i++, posts.lastIndex)]
                val h = if (rotated != null)
                    headersOf(HttpHeaders.ContentType to listOf("application/json"), HttpHeaders.SetCookie to listOf("XSRF-TOKEN=$rotated; path=/"))
                else json
                respond(body, HttpStatusCode.fromValue(status), h)
            }
        }
        return SofiaClient(engine, AcceptAllCookiesStorage())
    }

    @Test fun handshakesFirstAndSendsDecodedToken() = runBlocking {
        val log = Log()
        val r = client(log, listOf(Triple(200, VT_SAMPLE, null))).virtualTable("0328")
        assertTrue(r is SofiaResult.Ok)
        assertEquals(2, (r as SofiaResult.Ok).rows.size)
        assertEquals(listOf("GET", "POST token=hs1="), log.calls)
    }

    // Review focus 1: the token rotates; the next POST must use the new one, without a new handshake.
    @Test fun usesRotatedTokenOnNextCall() = runBlocking {
        val log = Log()
        val c = client(log, listOf(Triple(200, VT_SAMPLE, "rot%3D2"), Triple(200, VT_SAMPLE, null)))
        c.virtualTable("0328")
        c.virtualTable("0328")
        assertEquals(listOf("GET", "POST token=hs1=", "POST token=rot=2"), log.calls)
    }

    @Test fun expiredSessionRedoesHandshakeOnceAndRetries() = runBlocking {
        val log = Log()
        val r = client(log, listOf(Triple(419, "", null), Triple(200, VT_SAMPLE, null))).virtualTable("0328")
        assertTrue(r is SofiaResult.Ok)
        assertEquals(listOf("GET", "POST token=hs1=", "GET", "POST token=hs2="), log.calls)
    }

    @Test fun redirectAlsoTriggersHandshake() = runBlocking {
        val log = Log()
        val r = client(log, listOf(Triple(302, "", null), Triple(200, "[]", null))).virtualTable("0328")
        assertTrue(r is SofiaResult.Ok)
        assertEquals(0, (r as SofiaResult.Ok).rows.size)
    }

    @Test fun htmlTwiceIsUnavailable() = runBlocking {
        val log = Log()
        val r = client(log, listOf(Triple(200, "<html>maintenance</html>", null))).virtualTable("0328")
        assertTrue(r is SofiaResult.Unavailable)
        assertEquals(4, log.calls.size) // handshake, post, re-handshake, post — then give up
    }
}
