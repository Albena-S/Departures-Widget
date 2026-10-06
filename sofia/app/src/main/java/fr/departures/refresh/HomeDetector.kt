package fr.departures.refresh

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager

/** "Is the launcher the foreground app?" via UsageStatsManager (needs Usage access). */
object HomeDetector {
    private var cachedLauncher: String? = null
    private var cachedAt = 0L
    private var lastQueryAt = 0L
    private var lastEventAt = 0L
    private var lastPkg: String? = null

    /** null when Usage access is not granted. */
    fun isLauncherInFront(context: Context): Boolean? {
        if (!Perms.hasUsageAccess(context)) return null
        val launcher = launcherPackage(context) ?: return null
        val usm = context.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        // Incremental: only read events since the last tick. The first call after process start
        // looks back 6 h, because the last RESUMED event is old when the user stayed on home.
        val from = if (lastQueryAt == 0L) now - 6 * 60 * 60_000L else lastQueryAt - 2_000
        val events = usm.queryEvents(from, now)
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED && e.timeStamp >= lastEventAt) {
                lastPkg = e.packageName
                lastEventAt = e.timeStamp
            }
        }
        lastQueryAt = now
        return lastPkg == launcher
    }

    fun launcherPackage(context: Context): String? {
        val now = System.currentTimeMillis()
        if (cachedLauncher != null && now - cachedAt < 60_000) return cachedLauncher
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val pkg = context.packageManager.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        cachedLauncher = pkg?.takeIf { it != "android" } // "android" = chooser, no default set
        cachedAt = now
        return cachedLauncher
    }
}
