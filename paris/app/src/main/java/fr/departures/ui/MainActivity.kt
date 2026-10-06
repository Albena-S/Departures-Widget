package fr.departures.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.departures.data.ThemePref
import fr.departures.locator
import fr.departures.refresh.RefreshScheduler
import fr.departures.ui.theme.DeparturesTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        RefreshScheduler.ensureScheduled(this)
        val openSettings = intent.getStringExtra(EXTRA_OPEN) == OPEN_SETTINGS
        setContent {
            val settings by locator.settings.settings.collectAsStateWithLifecycle(initialValue = null)
            val s = settings ?: return@setContent
            DeparturesTheme(dark = s.theme == ThemePref.DARK) {
                AppNav(
                    start = when {
                        !s.onboardingDone -> Routes.ONBOARDING
                        openSettings -> Routes.SETTINGS
                        else -> Routes.GROUPS
                    },
                    onFinish = { finish() },
                    openSettingsSignal = settingsSignal,
                )
            }
        }
    }

    /** Bumped when the widget's "Add your API key" opens an already-running app (singleTask). */
    private var settingsSignal by mutableIntStateOf(0)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        if (intent.getStringExtra(EXTRA_OPEN) == OPEN_SETTINGS) settingsSignal++
    }

    companion object {
        const val EXTRA_OPEN = "open"
        const val OPEN_SETTINGS = "settings"
    }
}
