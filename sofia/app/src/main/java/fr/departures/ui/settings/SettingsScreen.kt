package fr.departures.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.departures.data.REFRESH_INTERVALS
import fr.departures.data.ThemePref
import fr.departures.locator
import fr.departures.refresh.Perms
import fr.departures.ui.onboarding.PermissionCard
import fr.departures.ui.onboarding.rememberPermState
import fr.departures.ui.onboarding.startSafely
import fr.departures.data.stops.StopsState
import androidx.compose.runtime.LaunchedEffect
import java.text.DateFormat
import java.util.Date
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val loc = context.locator
    val settings by loc.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    val perms = rememberPermState()

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Settings") },
            navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        )
    }) { pad ->
        val s = settings ?: return@Scaffold
        Column(
            Modifier.padding(pad).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Section("THEME")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ThemePref.entries.forEach { t ->
                    FilterChip(
                        selected = s.theme == t,
                        onClick = { scope.launch { loc.settings.update { it.copy(theme = t) } } },
                        label = { Text(if (t == ThemePref.LIGHT) "Light" else "Dark") },
                    )
                }
            }
            Text("Applies to the app and all widgets.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

            Section("REFRESH WHILE ON THE HOME SCREEN")
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                REFRESH_INTERVALS.forEach { sec ->
                    FilterChip(
                        selected = s.refreshIntervalSec == sec,
                        onClick = { scope.launch { loc.settings.update { it.copy(refreshIntervalSec = sec) } } },
                        label = { Text("Every $sec s") },
                    )
                }
            }
            Text(
                "About ${3 * 3600 / s.refreshIntervalSec} requests per stop for 3 h of home-screen time. The Sofia service has no published limit; longer intervals are kinder to it.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Section("SOFIA STOP LIST")
            StopListStatus()

            Section("PERMISSIONS")
            PermissionCard(
                "Usage access",
                if (perms.usage) "Refreshes only while the home screen is showing."
                else "Not allowed: the widget refreshes whenever the phone is unlocked, even inside other apps. That uses more of your quota.",
                perms.usage, "Open settings",
            ) { context.startSafely(Perms.usageAccessIntent(context), Perms.usageAccessFallbackIntent()) }
            if (Build.VERSION.SDK_INT >= 31) {
                PermissionCard(
                    "Alarms & reminders",
                    if (perms.alarms) "Checks run on time." else "Not allowed: updates may lag.",
                    perms.alarms, "Open settings",
                ) { context.startSafely(Perms.exactAlarmIntent(context)) }
            }
            PermissionCard(
                "Battery optimisation",
                if (perms.battery) "The app is exempt." else "If refreshes stop after a while, allow this first.",
                perms.battery, "Allow",
            ) { context.startSafely(Perms.batteryIntent(context), Perms.batteryFallbackIntent()) }

            if (fr.departures.BuildConfig.DEBUG) {
                Section("DEBUG")
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Fake departures", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "Shows every state (at platform, delayed, cancelled, clock time, empty) without calling the API. Tap the widget to apply.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(checked = s.fakeData, onCheckedChange = { on -> scope.launch { loc.settings.update { it.copy(fakeData = on) }; loc.departures.clearErrors() } })
                }
                OutlinedButton(onClick = {
                    scope.launch {
                        loc.sofia.resetSession()
                        loc.departures.clearErrors()
                        android.widget.Toast.makeText(context, "Session cleared. Next refresh does a new handshake.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Reset Sofia session") }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun Section(title: String) {
    Spacer(Modifier.height(12.dp))
    Text(title, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
}

@Composable
private fun StopListStatus() {
    val loc = LocalContext.current.locator
    val state by loc.stops.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { loc.stops.index() } // loads the count for "Ready"
    val text = when (val st = state) {
        StopsState.Missing -> "Not downloaded yet."
        is StopsState.Downloading -> "Downloading… ${st.bytes / 1024} KB"
        is StopsState.Ready -> (if (st.count > 0) "${st.count} stops" else "Ready") +
            ", updated " + DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date(st.updatedAt)) + ". Refreshed monthly."
        is StopsState.Failed -> st.message
    }
    Text(text, style = MaterialTheme.typography.bodyMedium)
    OutlinedButton(enabled = state !is StopsState.Downloading, onClick = { scope.launch { loc.stops.download() } }) { Text("Update now") }
}
