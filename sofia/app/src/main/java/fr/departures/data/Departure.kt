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
    /** Not provided by Sofia; kept so the shared widget code compiles unchanged. */
    val platform: String? = null,
    /** Destination stop id (`last_stop`): the stable direction key. */
    val destinationId: String = "",
)

enum class FetchReason(val tag: String) {
    HOME_ENTERED("home-entered"),
    INTERVAL("interval"),
    TAP("tap"),
}

enum class ErrorKind { NETWORK, RATE_LIMIT, UNAVAILABLE }

/** What the widget footer needs for one widget. */
data class WidgetDataState(
    val oldestFetchedAt: Long?,
    val error: ErrorKind?,
    val fetching: Boolean,
)
