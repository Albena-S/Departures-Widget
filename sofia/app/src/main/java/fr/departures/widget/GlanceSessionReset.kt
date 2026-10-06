package fr.departures.widget

import android.content.Context
import android.util.Log
import androidx.work.WorkManager
import fr.departures.data.TAG_REFRESH
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Glance draws each widget through a WorkManager "session" job, and only starts a new one when no
 * job for that widget is listed as running or enqueued (ExistingWorkPolicy.KEEP). After an app
 * update such a job can be left behind and never run: every update and tap for that widget is
 * then dropped, so the widget freezes while newly placed widgets work. Cancelling the jobs is safe
 * (this app uses WorkManager only through Glance); Glance starts fresh sessions on the next update.
 */
suspend fun resetGlanceSessions(context: Context, reason: String) {
    withContext(Dispatchers.IO) {
        runCatching { WorkManager.getInstance(context).cancelAllWork().result.get() }
            .onFailure { Log.w(TAG_REFRESH, "Glance session reset failed: ${it.message}") }
    }
    Log.i(TAG_REFRESH, "Glance sessions reset ($reason)")
    updateAllWidgets(context)
}
