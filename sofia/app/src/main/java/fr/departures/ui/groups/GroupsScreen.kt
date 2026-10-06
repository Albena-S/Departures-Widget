package fr.departures.ui.groups

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.res.stringResource
import fr.departures.R
import fr.departures.data.model.Group
import fr.departures.locator
import fr.departures.ui.LineBadge
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GroupsScreen(onOpen: (Long) -> Unit, onSettings: () -> Unit) {
    val context = LocalContext.current
    val repo = context.locator.groups
    val groups by repo.observeGroups().collectAsStateWithLifecycle(initialValue = null)
    val scope = rememberCoroutineScope()
    var creating by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf<Group?>(null) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.app_title)) },
                actions = { IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, "Settings") } },
            )
        },
        floatingActionButton = {
            if (!groups.isNullOrEmpty()) {
                ExtendedFloatingActionButton(onClick = { creating = true }, icon = { Icon(Icons.Filled.Add, null) }, text = { Text("New group") })
            }
        },
    ) { pad ->
        val list = groups
        when {
            list == null -> Unit
            list.isEmpty() -> EmptyState(Modifier.padding(pad)) { creating = true }
            else -> LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 8.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                itemsIndexed(list, key = { _, g -> g.id }) { i, g ->
                    GroupCard(
                        g,
                        canUp = i > 0, canDown = i < list.lastIndex,
                        onClick = { onOpen(g.id) },
                        onUp = { scope.launch { repo.moveGroup(g.id, i - 1) } },
                        onDown = { scope.launch { repo.moveGroup(g.id, i + 1) } },
                        onDelete = { deleting = g },
                    )
                }
            }
        }
    }

    if (creating) {
        TitleDialog(
            title = "New group",
            initial = "",
            confirm = "Create",
            onDismiss = { creating = false },
            onConfirm = { t ->
                creating = false
                scope.launch { onOpen(repo.createGroup(t)) }
            },
        )
    }
    deleting?.let { g ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete “${g.title}”?") },
            text = { Text("Widgets showing this group will ask you to open the app.") },
            confirmButton = { TextButton(onClick = { scope.launch { repo.deleteGroup(g.id) }; deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun EmptyState(modifier: Modifier, onCreate: () -> Unit) {
    Box(modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No groups yet", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "A group is a set of stops and lines shown together on one widget, like “Paris” or “School”.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(24.dp))
            Button(onClick = onCreate) { Text("Create a group") }
        }
    }
}

@Composable
fun GroupCard(
    g: Group,
    canUp: Boolean = false,
    canDown: Boolean = false,
    onClick: () -> Unit,
    onUp: (() -> Unit)? = null,
    onDown: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(Modifier.padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(g.title.ifBlank { "Untitled" }, style = MaterialTheme.typography.titleMedium)
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    g.entries.distinctBy { it.lineRef }.take(6).forEach { e ->
                        LineBadge(e.lineShortName, e.mode, e.lineColor, e.lineTextColor, heightDp = 18f)
                        Spacer(Modifier.width(4.dp))
                    }
                    Text(
                        "${g.entries.size} ${if (g.entries.size == 1) "entry" else "entries"}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
            if (onUp != null) IconButton(onClick = onUp, enabled = canUp) { Icon(Icons.Filled.KeyboardArrowUp, "Move up") }
            if (onDown != null) IconButton(onClick = onDown, enabled = canDown) { Icon(Icons.Filled.KeyboardArrowDown, "Move down") }
            if (onDelete != null) IconButton(onClick = onDelete) { Icon(Icons.Filled.Delete, "Delete") }
        }
    }
}

@Composable
fun TitleDialog(title: String, initial: String, confirm: String, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text, onValueChange = { text = it }, singleLine = true,
                label = { Text("Title") }, placeholder = { Text("Paris, Work, School…") },
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text.trim()) }, enabled = text.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
