package fr.departures.data.api

import android.util.Log
import fr.departures.data.FetchResult
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.client.statement.bodyAsText
import kotlinx.coroutines.CancellationException

const val TAG_API = "DeparturesApi"

class PrimClient(private val http: HttpClient) {

    suspend fun stopMonitoring(stopRef: String, apiKey: String): FetchResult {
        if (apiKey.isBlank()) return FetchResult.Unauthorized
        val started = System.currentTimeMillis()
        return try {
            val resp = http.get(URL) {
                parameter("MonitoringRef", stopRef)
                header("apikey", apiKey.trim())
                header("Accept", "application/json")
            }
            val code = resp.status.value
            val result = when {
                code == 401 || code == 403 -> FetchResult.Unauthorized
                code == 429 -> FetchResult.RateLimited
                code !in 200..299 -> FetchResult.Failure("HTTP $code")
                else -> FetchResult.Success(parseStopMonitoring(resp.bodyAsText()))
            }
            val count = (result as? FetchResult.Success)?.list?.size
            Log.i(TAG_API, "GET $stopRef -> $code count=$count ${System.currentTimeMillis() - started}ms")
            result
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Log.w(TAG_API, "GET $stopRef failed: ${e.javaClass.simpleName}: ${e.message}")
            FetchResult.Failure(e.message ?: e.javaClass.simpleName)
        }
    }

    companion object {
        const val URL = "https://prim.iledefrance-mobilites.fr/marketplace/stop-monitoring"
    }
}
