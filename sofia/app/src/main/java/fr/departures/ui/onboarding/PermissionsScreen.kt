package fr.departures.ui.onboarding

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.departures.data.stops.StopsState
import fr.departures.locator
import fr.departures.refresh.Perms
import kotlinx.coroutines.launch

data class PermState(val usage: Boolean, val alarms: Boolean, val battery: Boolean)

/** Re-reads the three special accesses each time the screen resumes (user returns from Settings). */
@Composable
fun rememberPermState(): PermState {
    val context = LocalContext.current
    var tick by remember { mutableIntStateOf(0) }
    LifecycleResumeEffect(Unit) {
        tick++
        onPauseOrDispose { }
    }
    return remember(tick) {
        PermState(Perms.hasUsageAccess(context), Perms.canExactAlarm(context), Perms.ignoresBatteryOptimisation(context))
    }
}

fun Context.startSafely(primary: Intent, fallback: Intent? = null) {
    runCatching { startActivity(primary) }.recoverCatching { if (fallback != null) startActivity(fallback) }
}

@Composable
fun PermissionsScreen(onContinue: () -> Unit) {
    val context = LocalContext.current
    val s = rememberPermState()
    Surface(Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text("Before you start", style = MaterialTheme.typography.headlineSmall)
            StopListCard()
            Text(
                "The widget only fetches departures while your home screen is showing. That saves battery and is kind to the Sofia service, but Android needs three permissions for it.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            PermissionCard(
                title = "Usage access",
                body = "Lets the app see when the home screen is in front, so it refreshes only then. Without it, the widget refreshes whenever the phone is unlocked.",
                granted = s.usage,
                action = "Open settings",
            ) { context.startSafely(Perms.usageAccessIntent(context), Perms.usageAccessFallbackIntent()) }
            if (Build.VERSION.SDK_INT >= 31) {
                PermissionCard(
                    title = "Alarms & reminders",
                    body = "Lets the app check every few seconds, on time. Without it, updates may lag.",
                    granted = s.alarms,
                    action = "Open settings",
                ) { context.startSafely(Perms.exactAlarmIntent(context)) }
            }
            PermissionCard(
                title = "Battery optimisation",
                body = "Some phones stop background apps aggressively. Allowing this keeps refreshes running.",
                granted = s.battery,
                action = "Allow",
                extra = {
                    TextButton(onClick = {
                        context.startSafely(Intent(Intent.ACTION_VIEW, Uri.parse(Perms.dontKillMyAppUrl())))
                    }) { Text("Tips for ${Build.MANUFACTURER}") }
                },
            ) { context.startSafely(Perms.batteryIntent(context), Perms.batteryFallbackIntent()) }
            Spacer(Modifier.height(4.dp))
            Button(onClick = onContinue, modifier = Modifier.fillMaxWidth()) {
                Text(if (s.usage && s.alarms && s.battery) "Continue" else "Continue anyway")
            }
        }
    }
}

@Composable
fun PermissionCard(
    title: String,
    body: String,
    granted: Boolean,
    action: String,
    extra: (@Composable () -> Unit)? = null,
    grantedLabel: String = "Allowed",
    showAction: Boolean = !granted,
    onClick: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                if (granted) {
                    Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.tertiary)
                    Text(" $grantedLabel", color = MaterialTheme.colorScheme.tertiary, style = MaterialTheme.typography.labelLarge)
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(body, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            if (showAction) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedButton(onClick = onClick) { Text(action) }
                    extra?.invoke()
                }
            }
        }
    }
}

/** First run: the Sofia stop list (for search by name or code) downloads here, with progress. */
@Composable
private fun StopListCard() {
    val loc = LocalContext.current.locator
    val state by loc.stops.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { loc.stops.ensure() }
    val ready = state is StopsState.Ready
    PermissionCard(
        title = "Sofia stop list",
        body = when (val st = state) {
            StopsState.Missing, is StopsState.Downloading ->
                "Downloading the official stop list" + ((st as? StopsState.Downloading)?.let { " (${it.bytes / 1024} KB)" } ?: "") + "…"
            is StopsState.Ready -> "Ready. Search stops by name (Cyrillic or Latin) or by the code on the sign."
            is StopsState.Failed -> st.message
        },
        granted = ready,
        action = "Retry",
        grantedLabel = "Ready",
        showAction = state is StopsState.Failed, // no Retry while it's still downloading
    ) { scope.launch { loc.stops.download() } }
}
