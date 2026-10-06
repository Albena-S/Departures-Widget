package fr.departures.refresh

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.util.Log
import fr.departures.data.TAG_REFRESH

/**
 * Chained, NON-wakeup alarms every ~5 s. RTC (not RTC_WAKEUP) alarms don't fire while the
 * device sleeps and are delivered as soon as it wakes, so a sleeping phone costs nothing.
 */
object RefreshScheduler {
    const val TICK_MS = 5_000L
    private const val REQ = 4242

    private fun pending(context: Context): PendingIntent =
        PendingIntent.getBroadcast(
            context, REQ,
            Intent(context, TickReceiver::class.java).setAction(TickReceiver.ACTION_TICK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    /** Arms the next tick soon. Safe to call often: it replaces any pending tick. */
    fun ensureScheduled(context: Context, delayMs: Long = 1_000L) = arm(context, delayMs)

    fun arm(context: Context, delayMs: Long = TICK_MS) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val at = System.currentTimeMillis() + delayMs
        if (Perms.canExactAlarm(context)) {
            am.setExact(AlarmManager.RTC, at, pending(context))
        } else {
            am.set(AlarmManager.RTC, at, pending(context))
        }
    }

    fun cancel(context: Context) {
        val am = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(context))
        Log.i(TAG_REFRESH, "ticks cancelled (no widgets)")
    }
}
