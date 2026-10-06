package fr.departures.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import fr.departures.data.FetchReason
import fr.departures.data.ThemePref
import fr.departures.locator
import fr.departures.refresh.RefreshScheduler
import fr.departures.ui.AppNav
import fr.departures.ui.Routes
import fr.departures.ui.theme.DeparturesTheme
import kotlinx.coroutines.launch

/** Opens when a widget is dropped on the home screen: "Which group?". Back = widget not added. */
class WidgetConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        appWidgetId = intent?.extras?.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
            ?: AppWidgetManager.INVALID_APPWIDGET_ID
        // Default: cancelled, so backing out does not add the widget.
        setResult(RESULT_CANCELED, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }
        setContent {
            val settings by locator.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            val s = settings ?: return@setContent
            DeparturesTheme(dark = s.theme == ThemePref.DARK) {
                AppNav(
                    start = if (s.onboardingDone) Routes.PICK else Routes.ONBOARDING,
                    onFinish = { finish() },
                    onGroupChosen = ::bind,
                )
            }
        }
    }

    private fun bind(groupId: Long) {
        val app = applicationContext
        val loc = app.locator
        lifecycleScope.launch {
            loc.groups.bindWidget(appWidgetId, groupId)
            updateWidget(app, appWidgetId)
            setResult(RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId))
            RefreshScheduler.ensureScheduled(app)
            // Fetch now so the new widget fills in right away (outlives this activity).
            loc.scope.launch {
                loc.departures.refresh(FetchReason.HOME_ENTERED)
                updateAllWidgets(app)
            }
            finish()
        }
    }
}
