package fr.departures.data.stops

import android.content.Context
import android.util.Log
import fr.departures.data.AppJson
import io.ktor.client.HttpClient
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.jvm.javaio.toInputStream
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.builtins.ListSerializer
import java.io.File
import java.io.FilterInputStream
import java.io.InputStream
import java.util.zip.ZipInputStream

sealed interface StopsState {
    data object Missing : StopsState
    data class Downloading(val bytes: Long) : StopsState
    data class Ready(val count: Int, val updatedAt: Long) : StopsState
    data class Failed(val message: String) : StopsState
}

/**
 * Sofia stop list from the official GTFS static feed. The zip is ~19 MB, but `stops.txt` is its
 * second entry, so we stream it and stop reading right after `stops.txt` (~130 KB on the wire).
 */
class SofiaStopRepository(context: Context, private val http: HttpClient) {
    private val file = File(context.filesDir, "stops.json")
    private val ser = ListSerializer(SofiaStop.serializer())
    private val lock = Mutex()
    private val _state = MutableStateFlow<StopsState>(if (file.exists()) StopsState.Ready(-1, file.lastModified()) else StopsState.Missing)
    val state: StateFlow<StopsState> = _state
    @Volatile private var cached: StopIndex? = null

    suspend fun index(): StopIndex? = cached ?: withContext(Dispatchers.IO) {
        lock.withLock {
            cached ?: runCatching { StopIndex(AppJson.decodeFromString(ser, file.readText())) }.getOrNull()?.also {
                cached = it
                _state.value = StopsState.Ready(it.stops.size, file.lastModified())
            }
        }
    }

    suspend fun ensure(): Boolean = if (file.exists() && index() != null) true else download(maxAgeMs = Long.MAX_VALUE)

    suspend fun refreshIfOlderThan(maxAgeMs: Long) {
        download(maxAgeMs = maxAgeMs)
    }

    /**
     * Downloads unless a list younger than [maxAgeMs] exists (checked inside the lock, so the
     * onboarding screen and App.onCreate never download twice). null = always ("Update now").
     */
    suspend fun download(maxAgeMs: Long? = null): Boolean = withContext(Dispatchers.IO) {
        lock.withLock {
            if (maxAgeMs != null && file.exists() && System.currentTimeMillis() - file.lastModified() <= maxAgeMs) return@withLock true
            _state.value = StopsState.Downloading(0)
            try {
                val csv = downloadStopsCsv(http) { _state.value = StopsState.Downloading(it) }
                val stops = parseStopsCsv(csv)
                if (stops.size < 100) error("Only ${stops.size} stops, refusing to replace the list")
                // Temp file + rename: an interrupted download never leaves a half-written list.
                val tmp = File(file.parentFile, "stops.json.tmp")
                tmp.writeText(AppJson.encodeToString(ser, stops))
                if (!tmp.renameTo(file)) { file.delete(); tmp.renameTo(file) }
                cached = StopIndex(stops)
                _state.value = StopsState.Ready(stops.size, file.lastModified())
                Log.i("DeparturesApi", "stop list: ${stops.size} stops")
                true
            } catch (e: CancellationException) {
                // Screen left mid-download: not a connection problem; let the next caller retry.
                _state.value = if (file.exists()) StopsState.Ready(cached?.stops?.size ?: -1, file.lastModified()) else StopsState.Missing
                throw e
            } catch (e: Exception) {
                Log.w("DeparturesApi", "stop list download failed: ${e.message}")
                _state.value = if (file.exists()) StopsState.Ready(cached?.stops?.size ?: -1, file.lastModified())
                else StopsState.Failed("Couldn't download the stop list. Check your connection.")
                false
            }
        }
    }

    companion object {
        const val GTFS_URL = "https://gtfs.sofiatraffic.bg/api/v1/static"
        const val MONTH_MS = 30L * 24 * 60 * 60 * 1000
    }
}

/**
 * Streams the GTFS zip and returns `stops.txt`. It is the second entry, so we stop reading right
 * after it; leaving `execute {}` closes the connection and the rest of the 19 MB is never fetched.
 * [onProgress] gets the compressed bytes read so far.
 */
suspend fun downloadStopsCsv(http: HttpClient, onProgress: (Long) -> Unit = {}): String =
    http.prepareGet(SofiaStopRepository.GTFS_URL).execute { resp ->
        if (resp.status.value !in 200..299) error("HTTP ${resp.status.value}")
        val counting = Counting(resp.bodyAsChannel().toInputStream(), onProgress)
        ZipInputStream(counting).use { zip ->
            generateSequence { zip.nextEntry }.firstOrNull { it.name == "stops.txt" } ?: error("stops.txt not found")
            zip.readBytes().toString(Charsets.UTF_8).also { onProgress(counting.count) }
        }
    }

private class Counting(input: InputStream, val onProgress: (Long) -> Unit) : FilterInputStream(input) {
    var count = 0L
        private set
    private var lastReport = 0L
    override fun read(): Int = super.read().also { if (it >= 0) bump(1) }
    override fun read(b: ByteArray, off: Int, len: Int): Int = super.read(b, off, len).also { if (it > 0) bump(it.toLong()) }
    private fun bump(k: Long) { count += k; if (count - lastReport > 16_384) { lastReport = count; onProgress(count) } }
}
