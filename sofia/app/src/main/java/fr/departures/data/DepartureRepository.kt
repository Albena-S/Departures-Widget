package fr.departures.data

import android.util.Log
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import fr.departures.data.api.SofiaClient
import fr.departures.data.api.SofiaResult
import fr.departures.data.api.toDepartures
import fr.departures.data.model.ALL_LINES
import fr.departures.data.model.Entry
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
    private val sofia: SofiaClient,
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
     * A tap bypasses network/unavailable backoff (the user asked) but never a 429 backoff.
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

            val outcomes = mutableListOf<StopOutcome>()
            var unavailableInARow = 0
            for ((i, ref) in refs.withIndex()) {
                if (i > 0) delay(220) // be polite: spaced requests, one per stop
                when (val r = sofia.virtualTable(ref)) {
                    is SofiaResult.Ok -> {
                        outcomes += StopOutcome.OK
                        unavailableInARow = 0
                        // Saved per stop, so a slow network still makes progress.
                        val c = CachedStop(r.fetchedAt, r.rows.toDepartures(r.fetchedAt))
                        store.edit { it[stopKey(ref)] = AppJson.encodeToString(CachedStop.serializer(), c) }
                    }
                    // Already retried with a fresh session inside the client. Keep going: one bad stop must
                    // not blank the others; but two in a row means the endpoint itself is down.
                    is SofiaResult.Unavailable -> {
                        outcomes += StopOutcome.UNAVAILABLE
                        if (++unavailableInARow >= 2) break
                    }
                    SofiaResult.RateLimited -> { outcomes += StopOutcome.RATE_LIMITED; break }
                    is SofiaResult.Failure -> outcomes += StopOutcome.NETWORK
                }
            }
            val successes = outcomes.count { it == StopOutcome.OK }
            val error = runError(outcomes)
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

    /** Called when the data source changes (fake data toggle, session reset): forget old error state. */
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

enum class StopOutcome { OK, NETWORK, UNAVAILABLE, RATE_LIMITED }

/**
 * Error for a whole refresh: rate limit wins; "unavailable" only when no stop worked;
 * any other miss just marks the data as stale (NETWORK).
 */
fun runError(outcomes: List<StopOutcome>): ErrorKind? = when {
    StopOutcome.RATE_LIMITED in outcomes -> ErrorKind.RATE_LIMIT
    outcomes.none { it == StopOutcome.OK } && StopOutcome.UNAVAILABLE in outcomes -> ErrorKind.UNAVAILABLE
    outcomes.any { it != StopOutcome.OK } -> ErrorKind.NETWORK
    else -> null
}

/** Interval refreshes are timed from the last attempt, not the last success (failures must not refetch every tick). */
fun intervalDue(now: Long, lastAttemptAt: Long?, intervalMs: Long): Boolean =
    lastAttemptAt == null || now - lastAttemptAt >= intervalMs - 500

/** Doubling backoff per consecutive failure, capped. */
fun backoffMs(kind: ErrorKind, n: Int): Long {
    val (base, cap) = when (kind) {
        ErrorKind.NETWORK -> 15_000L to 300_000L
        ErrorKind.RATE_LIMIT -> 30_000L to 600_000L
        ErrorKind.UNAVAILABLE -> 15_000L to 300_000L
    }
    return minOf(base shl n.coerceIn(0, 16), cap)
}

/**
 * Pure filter, unit-tested: matching line (or any line for [ALL_LINES]), wanted destination ids,
 * first 3. Sofia minutes are whole, so a departure stays until 60 s after its computed time
 * (otherwise a "0" would vanish seconds after the fetch).
 */
fun filterForEntry(all: List<Departure>, entry: Entry, now: Long): List<Departure> =
    all.asSequence()
        .filter { entry.lineRef == ALL_LINES || it.lineRef == entry.lineRef }
        .filter { entry.directions.isEmpty() || it.destinationId in entry.directions }
        .filter { it.expected >= now - 60_000 }
        .sortedBy { it.expected }
        .take(3)
        .toList()
