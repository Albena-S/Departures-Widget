package fr.departures.data

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import fr.departures.data.api.PrimClient
import fr.departures.data.model.Entry
import fr.departures.data.model.lineCode
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.serialization.Serializable

const val TAG_REFRESH = "DeparturesRefresh"

@Serializable
data class CachedStop(val fetchedAt: Long, val departures: List<Departure>)

/**
 * One request per unique stop per refresh, results cached per stop (survives process death).
 * Widgets filter the cache locally per entry.
 */
class DepartureRepository(
    private val store: DataStore<Preferences>,
    private val prim: PrimClient,
    private val settings: SettingsRepository,
    private val groups: GroupRepository,
) {
    private val errorKey = stringPreferencesKey("last_error")
    private val failCountKey = intPreferencesKey("fail_count")
    private val lastAttemptKey = longPreferencesKey("last_attempt_at")
    private val nextAllowedKey = longPreferencesKey("next_allowed_at")
    private fun stopKey(ref: String) = stringPreferencesKey("stop:$ref")

    private val mutex = Mutex()
    private val _fetching = MutableStateFlow(false)
    val fetching: StateFlow<Boolean> = _fetching

    /**
     * Fetches each stop once. Returns false when skipped (already running, backoff, nothing to fetch).
     * A tap bypasses network/auth backoff (the user asked) but never a 429 backoff.
     */
    suspend fun refresh(reason: FetchReason, stopRefs: Set<String>? = null): Boolean {
        if (!mutex.tryLock()) {
            Log.d(TAG_REFRESH, "skip reason=${reason.tag}: refresh already running")
            return false
        }
        try {
            val refs = stopRefs ?: groups.boundStopRefs()
            if (refs.isEmpty()) return false
            val now = System.currentTimeMillis()
            val prefs = store.data.first()
            val nextAllowed = prefs[nextAllowedKey] ?: 0L
            val lastErr = prefs[errorKey]?.let { runCatching { ErrorKind.valueOf(it) }.getOrNull() }
            val tapOverride = reason == FetchReason.TAP && lastErr != ErrorKind.RATE_LIMIT
            if (now < nextAllowed && !tapOverride) {
                Log.i(TAG_REFRESH, "skip reason=${reason.tag}: backoff after $lastErr, ${(nextAllowed - now) / 1000}s left")
                return false
            }
            _fetching.value = true
            // Recorded before the network so a fetch cut short by the tick timeout still counts.
            store.edit { it[lastAttemptKey] = now }
            val s = settings.current()
            Log.i(TAG_REFRESH, "fetch reason=${reason.tag} stops=${refs.size} ${refs.joinToString()}")

            if (s.fakeData) {
                val fake = FakeDepartures.generate(groups.getGroups(), now)
                store.edit { p ->
                    refs.forEach { ref ->
                        p[stopKey(ref)] = AppJson.encodeToString(CachedStop.serializer(), CachedStop(now, fake[ref].orEmpty()))
                    }
                    clearFailure(p)
                }
                return true
            }

            val key = settings.effectiveApiKey()
            var error: ErrorKind? = null
            var successes = 0
            for ((i, ref) in refs.withIndex()) {
                if (i > 0) delay(220) // stay under 5 requests/second
                when (val r = prim.stopMonitoring(ref, key)) {
                    is FetchResult.Success -> {
                        successes++
                        // Saved per stop, so a slow network still makes progress.
                        store.edit { it[stopKey(ref)] = AppJson.encodeToString(CachedStop.serializer(), CachedStop(System.currentTimeMillis(), r.list)) }
                    }
                    FetchResult.Unauthorized -> { error = ErrorKind.AUTH; break }
                    FetchResult.RateLimited -> { error = ErrorKind.RATE_LIMIT; break }
                    is FetchResult.Failure -> if (error == null) error = ErrorKind.NETWORK
                }
            }
            store.edit { p ->
                val err = error
                when {
                    err == null -> clearFailure(p)
                    // Some stops worked: keep the normal cadence, just flag staleness.
                    err == ErrorKind.NETWORK && successes > 0 -> { p[errorKey] = err.name; p.remove(failCountKey); p.remove(nextAllowedKey) }
                    else -> {
                        val n = p[failCountKey] ?: 0
                        val wait = backoffMs(err, n)
                        p[errorKey] = err.name
                        p[failCountKey] = n + 1
                        p[nextAllowedKey] = System.currentTimeMillis() + wait
                        Log.w(TAG_REFRESH, "$err: backing off ${wait / 1000}s (failure #${n + 1})")
                    }
                }
            }
            return true
        } finally {
            _fetching.value = false
            mutex.unlock()
        }
    }

    private fun clearFailure(p: androidx.datastore.preferences.core.MutablePreferences) {
        p.remove(errorKey)
        p.remove(failCountKey)
        p.remove(nextAllowedKey)
    }

    /** Called when the key or data source changes: forget old auth/rate-limit state. */
    suspend fun clearErrors() {
        store.edit { clearFailure(it) }
    }

    suspend fun lastAttemptAt(): Long? = store.data.first()[lastAttemptKey]

    /** True while [refresh] is running (shows the spinner). Set before the first render of a fetch. */
    fun markFetching() { _fetching.value = true }

    suspend fun cached(stopRef: String): CachedStop? =
        store.data.first()[stopKey(stopRef)]?.let {
            runCatching { AppJson.decodeFromString(CachedStop.serializer(), it) }.getOrNull()
        }

    /** Up to 3 upcoming departures for an entry, from cache. Cancelled ones count. */
    suspend fun departuresFor(entry: Entry, now: Long = System.currentTimeMillis()): List<Departure> =
        filterForEntry(cached(entry.stopRef)?.departures.orEmpty(), entry, now)

    suspend fun lastError(): ErrorKind? =
        store.data.first()[errorKey]?.let { runCatching { ErrorKind.valueOf(it) }.getOrNull() }

    suspend fun state(stopRefs: Set<String>): WidgetDataState {
        val oldest = stopRefs.mapNotNull { cached(it)?.fetchedAt }.minOrNull()
        return WidgetDataState(oldestFetchedAt = oldest, error = lastError(), fetching = fetching.value)
    }
}

/** Interval refreshes are timed from the last attempt, not the last success (failures must not refetch every tick). */
fun intervalDue(now: Long, lastAttemptAt: Long?, intervalMs: Long): Boolean =
    lastAttemptAt == null || now - lastAttemptAt >= intervalMs - 500

/** Doubling backoff per consecutive failure, capped. */
fun backoffMs(kind: ErrorKind, n: Int): Long {
    val (base, cap) = when (kind) {
        ErrorKind.NETWORK -> 15_000L to 300_000L
        ErrorKind.RATE_LIMIT -> 30_000L to 600_000L
        ErrorKind.AUTH -> 60_000L to 1_800_000L
    }
    return minOf(base shl n.coerceIn(0, 16), cap)
}

/** Pure filter, unit-tested: matching line, wanted direction, not already gone, first 3. */
fun filterForEntry(all: List<Departure>, entry: Entry, now: Long): List<Departure> {
    val code = lineCode(entry.lineRef)
    return all.asSequence()
        .filter { lineCode(it.lineRef) == code }
        .filter { entry.directions.isEmpty() || it.destination in entry.directions }
        .filter { it.atStop || it.expected >= now } // a train that has left must not show "0"
        .sortedBy { it.expected }
        .take(3)
        .toList()
}
