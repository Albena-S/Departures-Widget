package fr.departures.refresh

import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.PowerManager
import android.util.Log
import androidx.core.content.edit
import fr.departures.data.FetchReason
import fr.departures.data.TAG_REFRESH
import fr.departures.data.intervalDue
import fr.departures.locator
import fr.departures.widget.liveWidgetIds
import fr.departures.widget.updateAllWidgets
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * One ~5 s tick. No network unless the screen is on, unlocked and the launcher is in front.
 * Fetch on entering the home screen and then every refresh interval; re-render every tick.
 */
class TickReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val result = goAsync()
        app.locator.scope.launch {
            try {
                withTimeoutOrNull(9_000) { tick(app) }
            } catch (e: Exception) {
                Log.w(TAG_REFRESH, "tick failed: ${e.message}")
            } finally {
                // Re-arm no matter what happened above, as long as a widget is placed.
                // Uses the system's widget list, not our storage, so a storage error can't break the chain.
                if (liveWidgetIds(app).isNotEmpty()) RefreshScheduler.arm(app)
                else Log.d(TAG_REFRESH, "no widgets placed, stopping ticks")
                result.finish()
            }
        }
    }

    private suspend fun tick(context: Context) {
        if (liveWidgetIds(context).isEmpty()) return
        val loc = context.locator
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val wasHome = prefs.getBoolean(KEY_WAS_HOME, false)

        val screenOn = (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isInteractive
        val locked = (context.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked
        val home = if (!screenOn || locked) false else HomeDetector.isLauncherInFront(context) ?: true

        if (!home) {
            if (wasHome) {
                prefs.edit { putBoolean(KEY_WAS_HOME, false) }
                Log.d(TAG_REFRESH, "left home (screenOn=$screenOn locked=$locked)")
            }
            return
        }

        val intervalMs = loc.settings.current().refreshIntervalSec * 1000L
        val reason = when {
            !wasHome -> FetchReason.HOME_ENTERED
            intervalDue(System.currentTimeMillis(), loc.departures.lastAttemptAt(), intervalMs) -> FetchReason.INTERVAL
            else -> null
        }
        if (!wasHome) prefs.edit { putBoolean(KEY_WAS_HOME, true) }
        if (reason != null) {
            loc.departures.markFetching()
            updateAllWidgets(context) // shows the spinner
            loc.departures.refresh(reason)
        }
        updateAllWidgets(context) // minutes are recomputed from absolute times on every tick
    }

    companion object {
        const val ACTION_TICK = "fr.departures.TICK"
        private const val PREFS = "refresh_state"
        private const val KEY_WAS_HOME = "was_home"
    }
}
