package fr.departures.refresh

import android.annotation.SuppressLint
import android.app.AlarmManager
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.os.Process
import android.provider.Settings

/** Special-access checks and the intents that open the matching system screens. */
object Perms {
    fun hasUsageAccess(context: Context): Boolean {
        val ops = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        // String-op checkOpNoThrow exists on every supported API level and is not deprecated on 37.
        @Suppress("DEPRECATION")
        val mode = ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), context.packageName)
        return mode == AppOpsManager.MODE_ALLOWED
    }

    fun canExactAlarm(context: Context): Boolean =
        Build.VERSION.SDK_INT < 31 || (context.getSystemService(Context.ALARM_SERVICE) as AlarmManager).canScheduleExactAlarms()

    fun ignoresBatteryOptimisation(context: Context): Boolean =
        (context.getSystemService(Context.POWER_SERVICE) as PowerManager).isIgnoringBatteryOptimizations(context.packageName)

    fun usageAccessIntent(context: Context): Intent =
        Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS).apply {
            // Some OEMs support jumping straight to this app's toggle.
            data = Uri.parse("package:${context.packageName}")
        }

    fun usageAccessFallbackIntent(): Intent = Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)

    fun exactAlarmIntent(context: Context): Intent =
        if (Build.VERSION.SDK_INT >= 31) Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, Uri.parse("package:${context.packageName}"))
        else Intent(Settings.ACTION_SETTINGS)

    @SuppressLint("BatteryLife") // sideloaded personal app; the direct prompt is the point
    fun batteryIntent(context: Context): Intent =
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))

    fun batteryFallbackIntent(): Intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)

    fun dontKillMyAppUrl(): String =
        "https://dontkillmyapp.com/" + Build.MANUFACTURER.lowercase().replace(' ', '-')
}
