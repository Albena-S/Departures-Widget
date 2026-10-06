package fr.departures.refresh

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import fr.departures.locator
import fr.departures.widget.resetGlanceSessions
import kotlinx.coroutines.launch

/** Re-arms the tick chain after reboot and app updates, and unfreezes widgets left from before. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED -> {
                RefreshScheduler.ensureScheduled(context)
                val app = context.applicationContext
                val pending = goAsync()
                app.locator.scope.launch {
                    try { resetGlanceSessions(app, intent.action ?: "boot") } finally { pending.finish() }
                }
            }
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED" ->
                RefreshScheduler.ensureScheduled(context)
        }
    }
}
