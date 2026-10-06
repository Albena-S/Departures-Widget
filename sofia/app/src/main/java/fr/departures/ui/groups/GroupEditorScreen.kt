package fr.departures.ui.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.departures.data.model.Entry
import fr.departures.locator
import fr.departures.ui.LineBadge
import fr.departures.ui.label
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupEditorScreen(groupId: Long, onBack: () -> Unit, onAddEntry: () -> Unit, onUseForWidget: (() -> Unit)?) {
    val context = LocalContext.current
    val repo = context.locator.groups
    val group by repo.observeGroup(groupId).collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    var title by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(group?.id) { if (title == null && group != null) title = group!!.title }
    LaunchedEffect(title) {
        val t = title ?: return@LaunchedEffect
        delay(400)
        if (t.isNotBlank() && t.trim() != group?.title) repo.renameGroup(groupId, t)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit group") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        bottomBar = {
            if (onUseForWidget != null) {
                Button(
                    onClick = onUseForWidget,
                    enabled = !group?.entries.isNullOrEmpty() && !title.isNullOrBlank(),
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                ) { Text("Use this group for the widget") }
            }
        },
    ) { pad ->
        val g = group ?: return@Scaffold
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 8.dp, bottom = pad.calculateBottomPadding() + 32.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                OutlinedTextField(
                    value = title ?: "",
                    onValueChange = { title = it },
                    label = { Text("Title") },
                    placeholder = { Text("Paris, Work, School…") },
                    singleLine = true,
                    isError = title?.isBlank() == true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(12.dp))
                Text("STOPS AND LINES", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.outline)
            }
            if (g.entries.isEmpty()) {
                item {
                    Text(
                        "Nothing here yet. Add a station and the lines you take from it.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
            itemsIndexed(g.entries, key = { _, e -> e.id }) { i, e ->
                EntryRow(
                    e,
                    canUp = i > 0, canDown = i < g.entries.lastIndex,
                    onUp = { scope.launch { repo.moveEntry(e.id, i - 1) } },
                    onDown = { scope.launch { repo.moveEntry(e.id, i + 1) } },
                    onRemove = { scope.launch { repo.removeEntry(e.id) } },
                )
            }
            item {
                OutlinedButton(onClick = onAddEntry, modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                    Icon(Icons.Filled.Add, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Add station and lines")
                }
            }
        }
    }
}

@Composable
private fun EntryRow(e: Entry, canUp: Boolean, canDown: Boolean, onUp: () -> Unit, onDown: () -> Unit, onRemove: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Row(Modifier.padding(start = 12.dp, top = 10.dp, bottom = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            LineBadge(e.lineShortName, e.mode, e.lineColor, e.lineTextColor)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(e.stopName, style = MaterialTheme.typography.titleSmall)
                Text(
                    "${e.mode.label()} · " + if (e.directions.isEmpty()) "All directions" else "To " + e.directions.map { e.directionLabels[it] ?: it }.sorted().joinToString(", "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                )
            }
            IconButton(onClick = onUp, enabled = canUp) { Icon(Icons.Filled.KeyboardArrowUp, "Move up") }
            IconButton(onClick = onDown, enabled = canDown) { Icon(Icons.Filled.KeyboardArrowDown, "Move down") }
            IconButton(onClick = onRemove) { Icon(Icons.Filled.Close, "Remove") }
        }
    }
}
