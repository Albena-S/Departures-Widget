package fr.departures.ui.settings

import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.departures.BuildConfig
import fr.departures.data.FetchResult
import fr.departures.data.REFRESH_INTERVALS
import fr.departures.data.ThemePref
import fr.departures.locator
import fr.departures.refresh.Perms
import fr.departures.ui.onboarding.PermissionCard
import fr.departures.ui.onboarding.rememberPermState
import fr.departures.ui.onboarding.startSafely
import kotlinx.coroutines.launch

private const val TEST_STOP = "STIF:StopArea:SP:43105:" // Cernay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val loc = context.locator
    val settings by loc.settings.settings.collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    val perms = rememberPermState()
    var keyText by remember { mutableStateOf<String?>(null) }
    var showKey by remember { mutableStateOf(false) }
    var testResult by remember { mutableStateOf<String?>(null) }
    var testing by remember { mutableStateOf(false) }

    LaunchedEffect(settings != null) { if (keyText == null && settings != null) keyText = settings!!.apiKeyOverride.orEmpty() }

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
                "Roughly ${3 * 3600 / s.refreshIntervalSec} requests per stop for 3 h of home-screen time. Some PRIM keys allow only 1,000 a day.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Section("API KEY")
            OutlinedTextField(
                value = keyText.orEmpty(),
                onValueChange = { keyText = it; testResult = null },
                label = { Text("Your PRIM API key (optional)") },
                placeholder = { Text(if (BuildConfig.IDFM_API_KEY.isNotBlank()) "Using the built-in key" else "Paste your key") },
                singleLine = true,
                visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = { androidx.compose.material3.TextButton(onClick = { showKey = !showKey }) { Text(if (showKey) "Hide" else "Show") } },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                if (BuildConfig.IDFM_API_KEY.isNotBlank()) "Leave empty to use the key built into this app." else "This build has no built-in key, so paste one here.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Button(
                    enabled = (keyText.orEmpty().trim()) != (s.apiKeyOverride.orEmpty()),
                    onClick = {
                        scope.launch {
                            loc.settings.update { it.copy(apiKeyOverride = keyText?.trim()) }
                            keyText = keyText?.trim()
                            loc.departures.clearErrors() // a new key gets a fresh start, no leftover backoff
                            loc.departures.refresh(fr.departures.data.FetchReason.TAP)
                        }
                    },
                ) { Text("Save key") }
                Spacer(Modifier.width(8.dp))
                OutlinedButton(enabled = !testing, onClick = {
                    scope.launch {
                        testing = true
                        testResult = "Testing…"
                        val key = keyText?.trim()?.takeIf { it.isNotEmpty() } ?: BuildConfig.IDFM_API_KEY.trim()
                        testResult = when (val r = loc.prim.stopMonitoring(TEST_STOP, key)) {
                            is FetchResult.Success -> "OK: the key works (${r.list.size} departures at Cernay)."
                            FetchResult.Unauthorized -> if (key.isEmpty()) "No key to test." else "Refused: this key is not valid."
                            FetchResult.RateLimited -> "Valid key, but the daily or per-second quota is used up."
                            is FetchResult.Failure -> "Couldn't reach IDFM (${r.msg})."
                        }
                        testing = false
                    }
                }) { Text("Test API key") }
            }
            testResult?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }

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

            if (BuildConfig.DEBUG) {
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
