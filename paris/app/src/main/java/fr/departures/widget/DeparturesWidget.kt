package fr.departures.widget

import android.content.Context
import android.content.Intent
import android.os.Build
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.Action
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.CircularProgressIndicator
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.currentState
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.state.GlanceStateDefinition
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextDecoration
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import fr.departures.R
import fr.departures.data.AppJson
import fr.departures.data.model.Mode
import fr.departures.ui.MainActivity
import fr.departures.ui.theme.Palette
import fr.departures.ui.theme.palette

private val MODEL_KEY = stringPreferencesKey("model")

/** Below this height the widget drops the footer and uses compact rows. */
private val COMPACT_BELOW = 110.dp
private const val ROW_DP = 34f
private const val COMPACT_ROW_DP = 26f
class DeparturesWidget : GlanceAppWidget() {

    // Exact: we lay out from the real size (fit as many departures as the width allows).
    override val sizeMode = SizeMode.Exact

    override val stateDefinition: GlanceStateDefinition<*> = PreferencesGlanceStateDefinition

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // Render instantly from cache on first composition (e.g. after process death).
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(id)
        val initial = buildWidgetModel(context, appWidgetId)
        provideContent {
            val stored = currentState<Preferences>()[MODEL_KEY]
            val model = stored?.let { runCatching { AppJson.decodeFromString(WidgetModel.serializer(), it) }.getOrNull() }
                ?.takeIf { it.renderedAt >= initial.renderedAt } ?: initial
            WidgetContent(model)
        }
    }
}

/** Rebuild every placed widget's model from cache and push it. Cheap: no network. */
suspend fun updateAllWidgets(context: Context) {
    val manager = GlanceAppWidgetManager(context)
    val widget = DeparturesWidget()
    for (glanceId in manager.getGlanceIds(DeparturesWidget::class.java)) {
        val model = buildWidgetModel(context, manager.getAppWidgetId(glanceId))
        updateAppWidgetState(context, glanceId) { it[MODEL_KEY] = AppJson.encodeToString(WidgetModel.serializer(), model) }
        widget.update(context, glanceId)
    }
}

suspend fun updateWidget(context: Context, appWidgetId: Int) {
    val manager = GlanceAppWidgetManager(context)
    val glanceId = runCatching { manager.getGlanceIdBy(appWidgetId) }.getOrNull() ?: return
    val model = buildWidgetModel(context, appWidgetId)
    updateAppWidgetState(context, glanceId) { it[MODEL_KEY] = AppJson.encodeToString(WidgetModel.serializer(), model) }
    DeparturesWidget().update(context, glanceId)
}

private fun cp(c: Int): ColorProvider = ColorProvider(day = Color(c), night = Color(c))

@Composable
private fun WidgetContent(m: WidgetModel) {
    val context = LocalContext.current
    val p = palette(m.dark)
    val size = LocalSize.current
    // "short": no footer, no subheaders, inline station labels, age next to the title.
    val compact = size.height < COMPACT_BELOW
    val refresh = actionRunCallback<RefreshAction>()
    val openApp = actionStartActivity(Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    val openSettings = actionStartActivity(
        Intent(context, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            .putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_SETTINGS)
    )
    val rootAction: Action = when (m.kind) {
        WidgetKind.OK -> refresh
        WidgetKind.NO_KEY -> openSettings
        else -> openApp
    }

    var root = GlanceModifier.fillMaxSize().appWidgetBackground().clickable(rootAction)
    root = if (Build.VERSION.SDK_INT >= 31) {
        root.background(cp(p.bg)).cornerRadius(android.R.dimen.system_app_widget_background_radius)
    } else {
        root.background(ImageProvider(if (m.dark) R.drawable.widget_bg_dark else R.drawable.widget_bg_light))
    }
    val pad = if (compact) 10.dp else 14.dp

    Column(modifier = root.padding(horizontal = pad, vertical = pad - 2.dp)) {
        // Header
        Row(modifier = GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = m.title.ifEmpty { "Departures" },
                maxLines = 1,
                style = TextStyle(color = cp(p.ink), fontSize = 15.sp, fontWeight = FontWeight.Bold),
                modifier = GlanceModifier.defaultWeight(),
            )
            if (compact && m.kind == WidgetKind.OK) {
                Text(
                    text = shortAge(m.footer),
                    maxLines = 1,
                    style = TextStyle(color = cp(if (m.footerWarn) p.warn else p.ink3), fontSize = 10.sp),
                )
            }
        }
        Spacer(GlanceModifier.height(if (compact) 2.dp else 6.dp))

        when (m.kind) {
            WidgetKind.DELETED -> Message("This group was deleted. Open the app to choose another.", p, openApp)
            WidgetKind.UNBOUND -> Message("Open the app to choose a group.", p, openApp)
            WidgetKind.NO_KEY -> Message("Add your API key in the app", p, openSettings)
            WidgetKind.OK -> {
                // Height left for rows: widget minus vertical padding, title line and spacer.
                val bodyH = size.height.value - (pad.value - 2f) * 2 - 22f - (if (compact) 2f else 6f) - (if (compact) 0f else 24f)
                Box(
                    modifier = GlanceModifier.defaultWeight().fillMaxWidth(),
                    contentAlignment = if (compact) Alignment.CenterStart else Alignment.TopStart,
                ) {
                    Body(m, p, compact, size.width.value - pad.value * 2, bodyH, refresh)
                }
                if (!compact) Footer(m, p, refresh)
            }
        }
    }
}

@Composable
private fun Message(text: String, p: Palette, action: Action) {
    Box(modifier = GlanceModifier.fillMaxSize().clickable(action), contentAlignment = Alignment.CenterStart) {
        Text(text = text, style = TextStyle(color = cp(p.ink2), fontSize = 13.sp))
    }
}

/** A row plus its station name, shown inline only in compact mode for multi-station groups. */
private data class BodyRow(val row: RowModel, val station: String?)

private sealed interface BodyItem {
    val key: Long
    data class Header(val name: String, val first: Boolean, override val key: Long) : BodyItem
    data class Line(val row: BodyRow, val fit: RowFit, override val key: Long) : BodyItem
}

@Composable
private fun Body(m: WidgetModel, p: Palette, short: Boolean, contentWidthDp: Float, bodyHeightDp: Float, refresh: Action) {
    val multiStation = m.stations.size > 1
    val rowCount = m.stations.sumOf { it.rows.size }
    // A short widget with few rows has room for full-size rows: use it instead of leaving a gap.
    val compact = short && rowCount * ROW_DP > bodyHeightDp
    // Compact: station name inline on each row (no room for subheaders).
    // Taller: station subheaders with a hairline between stations.
    val inlineLabels = short && multiStation
    val headers = !short && multiStation
    val rows = m.stations.flatMap { s -> s.rows.map { BodyRow(it, if (inlineLabels) s.name else null) } }
    if (rows.isEmpty()) {
        Message("No lines in this group yet. Add some in the app.", p, refresh)
        return
    }
    // Mode pictograms only when there is plenty of room.
    val showPicto = !short && contentWidthDp >= 300f
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val fontScale = context.resources.configuration.fontScale
    val avail = contentWidthDp - if (showPicto) 20f else 0f
    fun fits(withLabels: Boolean) = rows.map { br ->
        val r = br.row
        val badge = BadgeRenderer.render(context, r.shortName, r.mode, r.color, r.textColor, if (compact) 18f else 22f)
        fitRow(avail, badge.width / density, r.cells.first(), r.cells.drop(1), if (withLabels) br.station else null, compact, fontScale)
    }
    // Inline labels on every row or on none, so the rows read consistently.
    val labelled = fits(withLabels = inlineLabels)
    val plan = if (labelled.all { it.showLabel == inlineLabels }) labelled else fits(withLabels = false)

    val items = buildList<BodyItem> {
        var i = 0
        m.stations.forEachIndexed { si, s ->
            if (headers) add(BodyItem.Header(s.name, si == 0, -(si + 1).toLong()))
            repeat(s.rows.size) {
                add(BodyItem.Line(rows[i], plan[i], rows[i].row.entryId))
                i++
            }
        }
    }
    val rowH = if (compact) COMPACT_ROW_DP else ROW_DP
    if (short && rowCount * rowH <= bodyHeightDp) {
        // Everything fits: plain column, centred vertically by the parent Box (no empty band at the bottom).
        Column(modifier = GlanceModifier.fillMaxWidth()) {
            items.forEach { item -> if (item is BodyItem.Line) LineRow(item.row, item.fit, p, compact, showPicto, refresh) }
        }
        return
    }
    LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
        items(items, itemId = { it.key }) { item ->
            when (item) {
                is BodyItem.Header -> StationHeader(item, p, refresh)
                is BodyItem.Line -> LineRow(item.row, item.fit, p, compact, showPicto, refresh)
            }
        }
    }
}

@Composable
private fun StationHeader(h: BodyItem.Header, p: Palette, refresh: Action) {
    Column(modifier = GlanceModifier.fillMaxWidth().clickable(refresh)) {
        if (!h.first) {
            Spacer(GlanceModifier.height(4.dp))
            Box(modifier = GlanceModifier.fillMaxWidth().height(1.dp).background(cp(p.rule))) {}
            Spacer(GlanceModifier.height(6.dp))
        }
        Text(
            text = h.name.uppercase(),
            maxLines = 1,
            style = TextStyle(color = cp(p.ink3), fontSize = 11.sp, fontWeight = FontWeight.Medium),
        )
    }
}

@Composable
private fun LineRow(br: BodyRow, fit: RowFit, p: Palette, compact: Boolean, showPicto: Boolean, refresh: Action) {
    val r = br.row
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val bmp = BadgeRenderer.render(context, r.shortName, r.mode, r.color, r.textColor, if (compact) 18f else 22f)
    val badgeDp = bmp.width / density

    Row(
        modifier = GlanceModifier.fillMaxWidth().height((if (compact) COMPACT_ROW_DP else ROW_DP).dp).clickable(refresh),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showPicto) {
            Image(
                provider = ImageProvider(modeIcon(r.mode)),
                contentDescription = null,
                modifier = GlanceModifier.size(14.dp),
                colorFilter = androidx.glance.ColorFilter.tint(cp(p.ink3)),
            )
            Spacer(GlanceModifier.width(6.dp))
        }
        Image(
            provider = ImageProvider(bmp),
            contentDescription = "Line ${r.shortName}",
            modifier = GlanceModifier.size(badgeDp.dp, (bmp.height / density).dp),
        )
        Spacer(GlanceModifier.width(if (compact) 8.dp else 10.dp))
        if (fit.showLabel) {
            // [H] Cernay ........ 4 min   34  12:55 — times right-aligned as one block.
            Text(
                text = br.station.orEmpty(),
                maxLines = 1,
                style = TextStyle(color = cp(p.ink2), fontSize = if (compact) 12.sp else 13.sp, fontWeight = FontWeight.Medium),
                modifier = GlanceModifier.defaultWeight(),
            )
            Spacer(GlanceModifier.width(8.dp))
            FirstCell(r.cells.first(), p, big = !compact)
        } else {
            // [H] 4 min ........ 34  12:55 — first departure stays next to its badge.
            FirstCell(r.cells.first(), p, big = !compact)
            Spacer(GlanceModifier.defaultWeight())
        }
        r.cells.drop(1).take(fit.laterCount).forEach { c ->
            Spacer(GlanceModifier.width(if (compact) 10.dp else 12.dp))
            LaterCell(c, p)
        }
    }
}

@Composable
private fun FirstCell(c: TimeCell, p: Palette, big: Boolean) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        FirstTime(c, p, big)
        c.platform?.let { Platform(it, p, big) }
    }
}

@Composable
private fun Platform(platform: String, p: Palette, big: Boolean) {
    val context = LocalContext.current
    val density = context.resources.displayMetrics.density
    val bmp = BadgeRenderer.renderPlatform(context, platform, stroke = p.ink3, text = p.ink2, heightDp = if (big) 17f else 15f)
    Spacer(GlanceModifier.width(6.dp))
    Image(
        provider = ImageProvider(bmp),
        contentDescription = "Platform $platform",
        modifier = GlanceModifier.size((bmp.width / density).dp, (bmp.height / density).dp),
    )
}

@Composable
private fun FirstTime(c: TimeCell, p: Palette, big: Boolean) {
    val numSize = if (big) 22.sp else 17.sp
    if (c.style == CellStyle.PULSE) {
        Pulse(p, big)
        return
    }
    val color = when (c.style) {
        CellStyle.DELAYED -> p.late
        CellStyle.CANCELLED -> p.muted
        CellStyle.EMPTY -> p.ink3
        else -> p.ink
    }
    val deco = if (c.style == CellStyle.CANCELLED) TextDecoration.LineThrough else null
    val (num, unit) = splitUnit(c.text)
    Row(verticalAlignment = Alignment.Bottom) {
        Text(text = num, style = TextStyle(color = cp(color), fontSize = numSize, fontWeight = FontWeight.Bold, textDecoration = deco))
        if (unit != null) {
            Text(
                text = " $unit",
                style = TextStyle(
                    color = cp(if (c.style == CellStyle.NORMAL) p.ink2 else color),
                    fontSize = if (big) 12.sp else 11.sp,
                    fontWeight = FontWeight.Medium,
                    textDecoration = deco,
                ),
                modifier = GlanceModifier.padding(bottom = if (big) 3.dp else 2.dp),
            )
        }
    }
}

@Composable
private fun LaterCell(c: TimeCell, p: Palette) {
    val color = when (c.style) {
        CellStyle.DELAYED -> p.late
        CellStyle.CANCELLED -> p.muted
        CellStyle.PULSE -> p.now
        else -> p.ink2
    }
    Text(
        text = laterText(c),
        maxLines = 1,
        style = TextStyle(
            color = cp(color),
            fontSize = 14.sp,
            fontWeight = if (c.style == CellStyle.PULSE) FontWeight.Bold else FontWeight.Medium,
            textDecoration = if (c.style == CellStyle.CANCELLED) TextDecoration.LineThrough else null,
        ),
    )
}

/** Pulsing "0": a RemoteViews ViewFlipper fading between full and 35 % alpha (Glance can't animate). */
@Composable
private fun Pulse(p: Palette, big: Boolean) {
    val context = LocalContext.current
    val rv = RemoteViews(context.packageName, if (big) R.layout.pulse_zero else R.layout.pulse_zero_small)
    rv.setTextColor(R.id.pulse_a, p.now)
    rv.setTextColor(R.id.pulse_b, p.now)
    // Fixed size: unconstrained, the embedded view grabs the whole row and pushes later times out.
    val fontScale = context.resources.configuration.fontScale
    val w = textWidth("0", if (big) 22f else 17f) * 1.1f * fontScale + 2f
    AndroidRemoteViews(
        remoteViews = rv,
        modifier = GlanceModifier.size(w.dp, (if (big) ROW_DP else COMPACT_ROW_DP).dp),
    )
}

@Composable
private fun Footer(m: WidgetModel, p: Palette, refresh: Action) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().padding(top = 4.dp).clickable(refresh),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = m.footer,
            maxLines = 1,
            style = TextStyle(color = cp(if (m.footerWarn) p.warn else p.ink3), fontSize = 11.sp),
            modifier = GlanceModifier.defaultWeight(),
        )
        if (m.fetching) {
            CircularProgressIndicator(modifier = GlanceModifier.size(14.dp), color = cp(p.ink3))
        } else {
            Image(
                provider = ImageProvider(R.drawable.ic_refresh),
                contentDescription = "Refresh",
                modifier = GlanceModifier.size(16.dp),
                colorFilter = androidx.glance.ColorFilter.tint(cp(p.ink3)),
            )
        }
    }
}

private fun splitUnit(text: String): Pair<String, String?> {
    val i = text.indexOf(' ')
    return if (i > 0) text.substring(0, i) to text.substring(i + 1) else text to null
}

/** "Updated 12 s ago" → "12 s" for the narrow header. */
private fun shortAge(footer: String): String = when {
    footer.startsWith("Updated just now") -> "now"
    footer.startsWith("Updated ") -> footer.removePrefix("Updated ").removeSuffix(" ago").substringBefore(" ·")
    footer.startsWith("Rate limit") -> "limit"
    else -> ""
}

private fun modeIcon(mode: Mode): Int = when (mode) {
    Mode.METRO -> R.drawable.ic_mode_metro
    Mode.RER -> R.drawable.ic_mode_rer
    Mode.TRAIN -> R.drawable.ic_mode_train
    Mode.TRAM -> R.drawable.ic_mode_tram
    Mode.BUS -> R.drawable.ic_mode_bus
    Mode.OTHER -> R.drawable.ic_mode_train
}

