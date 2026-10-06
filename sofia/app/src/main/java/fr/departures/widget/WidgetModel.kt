package fr.departures.widget

import android.content.Context
import fr.departures.BuildConfig
import fr.departures.data.ThemePref
import fr.departures.data.model.Mode
import fr.departures.locator
import kotlinx.serialization.Serializable
import java.time.Instant

@Serializable
enum class WidgetKind { OK, DELETED, UNBOUND }

@Serializable
data class RowModel(
    val entryId: Long,
    val shortName: String,
    val mode: Mode,
    val color: Int? = null,
    val textColor: Int? = null,
    val cells: List<TimeCell>,
)

@Serializable
data class StationModel(val name: String, val rows: List<RowModel>)

/** Everything one widget instance renders; stored in the widget's Glance state as JSON. */
@Serializable
data class WidgetModel(
    val kind: WidgetKind,
    val dark: Boolean,
    val title: String = "",
    val stations: List<StationModel> = emptyList(),
    val footer: String = "",
    val footerWarn: Boolean = false,
    val fetching: Boolean = false,
    val renderedAt: Long = 0,
)

suspend fun buildWidgetModel(context: Context, appWidgetId: Int, now: Long = System.currentTimeMillis()): WidgetModel {
    val loc = context.locator
    val settings = loc.settings.current()
    val dark = settings.theme == ThemePref.DARK
    val groupId = loc.groups.groupIdForWidget(appWidgetId) ?: return WidgetModel(WidgetKind.UNBOUND, dark, renderedAt = now)
    val group = loc.groups.getGroup(groupId) ?: return WidgetModel(WidgetKind.DELETED, dark, renderedAt = now)

    val stopRefs = group.entries.map { it.stopRef }.toSet()
    val state = loc.departures.state(stopRefs)

    val instant = Instant.ofEpochMilli(now)
    val stations = group.entries
        .groupBy { it.stopRef }          // keeps first-appearance order of stations
        .map { (_, entries) ->
            StationModel(
                name = entries.first().stopName,
                rows = entries.map { e ->
                    val deps = loc.departures.departuresFor(e, now)
                    val cells = deps.map { formatDeparture(it, instant) }.ifEmpty { listOf(emptyCell()) }
                    RowModel(e.id, e.lineShortName, e.mode, e.lineColor, e.lineTextColor, cells)
                },
            )
        }
    val (footer, warn) = footerText(state, instant)
    return WidgetModel(
        kind = WidgetKind.OK,
        dark = dark,
        title = group.title.ifBlank { "Departures" },
        stations = stations,
        footer = if (BuildConfig.DEBUG && settings.fakeData) "$footer · fake" else footer,
        footerWarn = warn,
        fetching = state.fetching,
        renderedAt = now,
    )
}
