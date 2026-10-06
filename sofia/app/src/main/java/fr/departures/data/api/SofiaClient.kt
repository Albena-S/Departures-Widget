package fr.departures.data.api

import android.util.Log
import io.ktor.client.HttpClient
import io.ktor.client.engine.HttpClientEngine
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.cookies.CookiesStorage
import io.ktor.client.plugins.cookies.HttpCookies
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.Url
import io.ktor.http.content.TextContent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.URLDecoder

const val TAG_API = "DeparturesApi"

sealed interface SofiaResult {
    data class Ok(val rows: List<VtRow>, val fetchedAt: Long) : SofiaResult
    /** Endpoint answered but not with data, even after a fresh session (HTML, redirects…). */
    data class Unavailable(val status: Int, val snippet: String) : SofiaResult
    /** HTTP 429: respect the rate-limit backoff (a tap doesn't override it). */
    data object RateLimited : SofiaResult
    /** Network problem or server error. */
    data class Failure(val msg: String) : SofiaResult
}

/**
 * sofiatraffic.bg "virtual table" (unofficial; what the website's stop boards use).
 * Session: GET the public page once for the XSRF-TOKEN + session cookies, then POST with the
 * url-decoded token as X-XSRF-TOKEN. The token rotates, so it's read from the cookie jar every time.
 */
class SofiaClient(engine: HttpClientEngine, private val cookies: CookiesStorage) {

    private val http = HttpClient(engine) {
        expectSuccess = false
        followRedirects = false // a 302 means "no session" here, not something to follow
        install(HttpCookies) { storage = cookies }
        install(HttpTimeout) {
            requestTimeoutMillis = 6_000
            connectTimeoutMillis = 5_000
        }
    }
    private val handshakeLock = Mutex()

    suspend fun virtualTable(code: String): SofiaResult = try {
        if (xsrfToken() == null) handshake()
        when (val first = post(code)) {
            is Attempt.Good -> SofiaResult.Ok(first.rows, first.fetchedAt)
            is Attempt.Final -> first.result
            is Attempt.NeedsSession -> {
                Log.i(TAG_API, "stop $code: ${first.status}, new session and retry")
                handshake()
                when (val second = post(code)) {
                    is Attempt.Good -> SofiaResult.Ok(second.rows, second.fetchedAt)
                    is Attempt.Final -> second.result
                    is Attempt.NeedsSession -> {
                        Log.w(TAG_API, "stop $code unavailable: ${second.status} ${second.snippet.take(300)}")
                        SofiaResult.Unavailable(second.status, second.snippet)
                    }
                    is Attempt.Error -> SofiaResult.Failure(second.msg)
                }
            }
            is Attempt.Error -> SofiaResult.Failure(first.msg)
        }
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Log.w(TAG_API, "stop $code failed: ${e.javaClass.simpleName}: ${e.message}")
        SofiaResult.Failure(e.message ?: e.javaClass.simpleName)
    }

    /** Forget the session (debug "Reset session" and tests of the recovery path). */
    suspend fun resetSession() {
        (cookies as? PersistentCookiesStorage)?.clear()
    }

    private sealed interface Attempt {
        data class Good(val rows: List<VtRow>, val fetchedAt: Long) : Attempt
        data class NeedsSession(val status: Int, val snippet: String) : Attempt
        data class Error(val msg: String) : Attempt
        /** No point retrying with a new session (rate limit, changed format). */
        data class Final(val result: SofiaResult) : Attempt
    }

    private suspend fun post(code: String): Attempt {
        val started = System.currentTimeMillis()
        val resp = http.post(TABLE_URL) {
            header("Accept", "application/json")
            xsrfToken()?.let { header("X-XSRF-TOKEN", it) }
            setBody(TextContent("""{"stop":"${code.filter { it.isLetterOrDigit() }}"}""", ContentType.Application.Json))
        }
        val status = resp.status.value
        val body = resp.bodyAsText()
        Log.i(TAG_API, "POST stop=$code -> $status ${body.length}B ${System.currentTimeMillis() - started}ms")
        if (status in 300..399 || status == 401 || status == 419) return Attempt.NeedsSession(status, body.take(300))
        if (status == 429) return Attempt.Final(SofiaResult.RateLimited)
        if (status !in 200..299) return Attempt.Error("HTTP $status")
        return try {
            Attempt.Good(parseVirtualTable(body), System.currentTimeMillis())
        } catch (e: NotJsonException) {
            Attempt.NeedsSession(status, body.take(300))
        } catch (e: FormatChangedException) {
            Log.w(TAG_API, "stop $code: response format changed: ${e.snippet}")
            Attempt.Final(SofiaResult.Unavailable(status, e.snippet))
        }
    }

    private suspend fun handshake() = handshakeLock.withLock {
        val resp = http.get(SESSION_URL)
        Log.i(TAG_API, "handshake -> ${resp.status.value}, token=${xsrfToken() != null}")
    }

    private suspend fun xsrfToken(): String? =
        cookies.get(Url(SESSION_URL)).firstOrNull { it.name == "XSRF-TOKEN" }?.value
            ?.let { if ('%' in it) URLDecoder.decode(it, "UTF-8") else it }

    companion object {
        const val SESSION_URL = "https://www.sofiatraffic.bg/bg/public-transport"
        const val TABLE_URL = "https://www.sofiatraffic.bg/bg/trip/getVirtualTable"
    }
}
