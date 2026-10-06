package fr.departures.ui.groups

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.departures.locator
import kotlinx.coroutines.launch

/** Widget placement: "Which group?" Existing groups plus "New group". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PickGroupScreen(onPick: (Long) -> Unit, onCreated: (Long) -> Unit, onCancel: () -> Unit) {
    val context = LocalContext.current
    val repo = context.locator.groups
    val groups by repo.observeGroups().collectAsStateWithLifecycle(initialValue = emptyList())
    val scope = rememberCoroutineScope()
    var creating by remember { mutableStateOf(false) }

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Which group?") },
            navigationIcon = { IconButton(onClick = onCancel) { Icon(Icons.Filled.Close, "Cancel") } },
        )
    }) { pad ->
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = pad.calculateTopPadding() + 8.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            items(groups, key = { it.id }) { g -> GroupCard(g, onClick = { onPick(g.id) }) }
            item {
                OutlinedButton(onClick = { creating = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, null)
                    Text("New group", modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
    if (creating) {
        TitleDialog("New group", "", "Create", onDismiss = { creating = false }) { t ->
            creating = false
            scope.launch { onCreated(repo.createGroup(t)) }
        }
    }
}

