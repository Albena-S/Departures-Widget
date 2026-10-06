package fr.departures.widget

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import fr.departures.locator
import fr.departures.refresh.RefreshScheduler
import kotlinx.coroutines.launch

/** Widget ids the system says are placed right now (source of truth over stored bindings). */
fun liveWidgetIds(context: Context): Set<Int> =
    AppWidgetManager.getInstance(context)
        .getAppWidgetIds(ComponentName(context, DeparturesWidgetReceiver::class.java))
        .toSet()

class DeparturesWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DeparturesWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        RefreshScheduler.ensureScheduled(context)
    }

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // Also restarts the tick chain after an OEM force-stop the next time the launcher updates us.
        RefreshScheduler.ensureScheduled(context)
    }

    override fun onDisabled(context: Context) {
        super.onDisabled(context)
        RefreshScheduler.cancel(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        val loc = context.locator
        loc.scope.launch { appWidgetIds.forEach { loc.groups.unbindWidget(it) } }
    }
}
