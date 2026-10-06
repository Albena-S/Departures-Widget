package fr.departures.data

import kotlinx.serialization.Serializable

/** One real-time departure at a stop. Times are epoch milliseconds (UTC instants). */
@Serializable
data class Departure(
    val lineRef: String,
    val destination: String,
    val expected: Long,
    val aimed: Long? = null,
    val cancelled: Boolean = false,
    val atStop: Boolean = false,
    /** Platform/track as published (normalised, e.g. "2", "B"); null when IDFM doesn't say. */
    val platform: String? = null,
)

sealed interface FetchResult {
    data class Success(val list: List<Departure>) : FetchResult
    data object Unauthorized : FetchResult
    data object RateLimited : FetchResult
    data class Failure(val msg: String) : FetchResult
}

enum class FetchReason(val tag: String) {
    HOME_ENTERED("home-entered"),
    INTERVAL("interval"),
    TAP("tap"),
}

enum class ErrorKind { NETWORK, AUTH, RATE_LIMIT }

/** What the widget footer needs for one widget. */
data class WidgetDataState(
    val oldestFetchedAt: Long?,
    val error: ErrorKind?,
    val fetching: Boolean,
)
