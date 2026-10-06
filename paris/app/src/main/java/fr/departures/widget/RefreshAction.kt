package fr.departures.widget

import android.content.Context
import androidx.glance.GlanceId
import androidx.glance.action.ActionParameters
import androidx.glance.appwidget.action.ActionCallback
import fr.departures.data.FetchReason
import fr.departures.locator

/** Tap anywhere on the widget: force a refresh of every placed widget's stops. */
class RefreshAction : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val repo = context.locator.departures
        repo.markFetching()
        updateAllWidgets(context) // spinner
        repo.refresh(FetchReason.TAP)
        updateAllWidgets(context)
    }
}
