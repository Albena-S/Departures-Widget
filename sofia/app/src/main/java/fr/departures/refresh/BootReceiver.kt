package fr.departures.refresh

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Re-arms the tick chain after reboot and app updates. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED, Intent.ACTION_MY_PACKAGE_REPLACED,
            "android.app.action.SCHEDULE_EXACT_ALARM_PERMISSION_STATE_CHANGED" ->
                RefreshScheduler.ensureScheduled(context)
        }
    }
}
