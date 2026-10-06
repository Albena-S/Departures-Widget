package fr.departures.ui.addentry

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.departures.data.api.SofiaResult
import fr.departures.data.api.VtRow
import fr.departures.data.model.ALL_LINES
import fr.departures.data.model.Entry
import fr.departures.data.model.Mode
import fr.departures.data.stops.SofiaStop
import fr.departures.data.stops.StopsState
import fr.departures.locator
import fr.departures.ui.LineBadge
import fr.departures.ui.label
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Step { SEARCH, LINES, DIRECTIONS }

/** Result of the one live call made after a stop is picked. */
private sealed interface Live {
    data object Loading : Live
    data class Ok(val rows: List<VtRow>) : Live
    data class Failed(val message: String) : Live
}

private const val WHITE = 0xFFFFFFFF.toInt()

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEntryFlow(groupId: Long, onDone: () -> Unit) {
    val context = LocalContext.current
    val loc = context.locator
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf(Step.SEARCH) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<SofiaStop>>(emptyList()) }
    val stopsState by loc.stops.state.collectAsStateWithLifecycle()

    var stop by remember { mutableStateOf<SofiaStop?>(null) }
    var attempt by remember { mutableStateOf(0) } // bumped on pick and on Retry, so the same stop re-fetches
    var live by remember { mutableStateOf<Live>(Live.Loading) }
    val selectedLines = remember { mutableStateMapOf<String, Boolean>() } // extId or ALL_LINES
    val chosenDirs = remember { mutableStateMapOf<String, Set<String>>() } // extId -> last_stop ids; empty = all

    BackHandler(enabled = step != Step.SEARCH) {
        step = if (step == Step.DIRECTIONS) Step.LINES else Step.SEARCH
    }

    LaunchedEffect(Unit) { loc.stops.ensure() }
    // 1. search (in memory, so only a short debounce)
    LaunchedEffect(query, stopsState) {
        delay(120)
        results = loc.stops.index()?.search(query).orEmpty()
    }
    // 2. one live call for the picked stop: lines and destinations both come from it
    LaunchedEffect(stop, attempt) {
        val s = stop ?: return@LaunchedEffect
        live = when (val r = loc.sofia.virtualTable(s.code)) {
            is SofiaResult.Ok -> Live.Ok(r.rows)
            is SofiaResult.Unavailable -> Live.Failed("Sofia data unavailable. Try again later.")
            SofiaResult.RateLimited -> Live.Failed("Too many requests. Wait a minute and retry.")
            is SofiaResult.Failure -> Live.Failed("Couldn't reach sofiatraffic.bg. Check your connection.")
        }
    }

    val rows = (live as? Live.Ok)?.rows.orEmpty()
    val lines = rows.distinctBy { it.extId }.sortedWith(compareBy({ Mode.fromSofiaType(it.type).ordinal }, { it.name.padStart(4, '0') }))
    // "All lines" only when the call worked and nothing is running (night), never as a fallback for an error.
    val allLinesOnly = live is Live.Ok && lines.isEmpty()
    val picked = lines.filter { selectedLines[it.extId] == true }
    val allPicked = selectedLines[ALL_LINES] == true

    fun save() {
        val s = stop ?: return
        scope.launch {
            val entries = if (allPicked) listOf(
                Entry(stopRef = s.code, stopName = s.name, lineRef = ALL_LINES, lineShortName = "All", mode = Mode.OTHER)
            ) else picked.map { l ->
                val dirs = chosenDirs[l.extId].orEmpty()
                Entry(
                    stopRef = s.code,
                    stopName = s.name,
                    lineRef = l.extId,
                    lineShortName = l.name,
                    mode = Mode.fromSofiaType(l.type),
                    lineColor = l.color,
                    lineTextColor = WHITE,
                    directions = dirs,
                    directionLabels = rows.filter { it.extId == l.extId && it.lastStop in dirs }.associate { it.lastStop to it.destination },
                )
            }
            loc.groups.addEntries(groupId, entries)
            onDone()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (step) {
                            Step.SEARCH -> "Find a stop"
                            Step.LINES -> stop?.let { "${it.name} · ${it.code}" } ?: "Lines"
                            Step.DIRECTIONS -> "Directions"
                        },
                        maxLines = 1,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        when (step) {
                            Step.SEARCH -> onDone()
                            Step.LINES -> step = Step.SEARCH
                            Step.DIRECTIONS -> step = Step.LINES
                        }
                    }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
                },
            )
        },
        bottomBar = {
            val mod = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp)
            when (step) {
                Step.LINES -> when {
                    allPicked -> Button(onClick = ::save, modifier = mod) { Text("Add to group") }
                    else -> Button(onClick = { step = Step.DIRECTIONS }, enabled = picked.isNotEmpty(), modifier = mod) {
                        Text(if (picked.isEmpty()) "Pick at least one line" else "Next")
                    }
                }
                Step.DIRECTIONS -> Button(onClick = ::save, modifier = mod) { Text("Add to group") }
                else -> Unit
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (step) {
                Step.SEARCH -> SearchStep(query, { query = it }, results, stopsState, onRetry = { scope.launch { loc.stops.download() } }) {
                    // Reset synchronously so the previous stop's lines never flash with "Next" enabled.
                    live = Live.Loading; selectedLines.clear(); chosenDirs.clear()
                    stop = it; attempt++; step = Step.LINES
                }
                Step.LINES -> LinesStep(live, lines, allLinesOnly, selectedLines, onRetry = { live = Live.Loading; attempt++ })
                Step.DIRECTIONS -> DirectionsStep(picked, rows, chosenDirs)
            }
        }
    }
}

@Composable
private fun SearchStep(
    query: String, onQuery: (String) -> Unit,
    results: List<SofiaStop>, state: StopsState, onRetry: () -> Unit,
    onPick: (SofiaStop) -> Unit,
) {
    Column(Modifier.fillMaxSize().imePadding()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            placeholder = { Text("Code on the sign (0328) or name") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        when (state) {
            StopsState.Missing, is StopsState.Downloading -> Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.width(18.dp).height(18.dp), strokeWidth = 2.dp)
                Spacer(Modifier.width(12.dp))
                Text("Downloading the stop list…", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is StopsState.Failed -> Column(Modifier.padding(16.dp)) {
                Text(state.message, color = MaterialTheme.colorScheme.error)
                OutlinedButton(onClick = onRetry) { Text("Retry") }
            }
            is StopsState.Ready -> if (query.isNotBlank() && results.isEmpty()) Hint("No stop found for “${query.trim()}”.")
        }
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            items(results, key = { it.code }) { s ->
                Column(Modifier.fillMaxWidth().clickable { onPick(s) }.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    // Several stops share a name (one per side of the street): the code tells them apart.
                    Text("${s.name} · ${s.code}", style = MaterialTheme.typography.titleMedium)
                    Text(
                        listOf(s.latin.uppercase(), s.modes.sortedBy { it.ordinal }.joinToString(" · ") { it.label() })
                            .filter { it.isNotBlank() }.joinToString(" — "),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}

@Composable
private fun LinesStep(live: Live, lines: List<VtRow>, allLinesOnly: Boolean, selected: MutableMap<String, Boolean>, onRetry: () -> Unit) {
    LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
        when (live) {
            Live.Loading -> item {
                Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
            }
            is Live.Failed -> item {
                Column {
                    Hint(live.message, error = true)
                    OutlinedButton(onClick = onRetry, modifier = Modifier.padding(horizontal = 16.dp)) { Text("Retry") }
                }
            }
            is Live.Ok -> if (lines.isEmpty()) item { Hint("No vehicles right now. Try again during service hours.") }
            else item { Hint("Pick the lines you take from here.") }
        }
        items(lines, key = { it.extId }) { l ->
            val on = selected[l.extId] == true
            Row(
                Modifier.fillMaxWidth().clickable { selected[l.extId] = !on }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LineBadge(l.name, Mode.fromSofiaType(l.type), l.color, WHITE, heightDp = 26f)
                Spacer(Modifier.width(14.dp))
                Text(Mode.fromSofiaType(l.type).label(), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Checkbox(checked = on, onCheckedChange = { selected[l.extId] = it })
            }
        }
        if (allLinesOnly) item {
            val on = selected[ALL_LINES] == true
            Row(
                Modifier.fillMaxWidth().clickable { selected[ALL_LINES] = !on }.padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                LineBadge("All", Mode.OTHER, null, null, heightDp = 26f)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text("All lines", style = MaterialTheme.typography.bodyLarge)
                    Text("Shows the next vehicles of any line at this stop.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Checkbox(checked = on, onCheckedChange = { selected[ALL_LINES] = it })
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DirectionsStep(picked: List<VtRow>, rows: List<VtRow>, chosen: MutableMap<String, Set<String>>) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        items(picked, key = { it.extId }) { l ->
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LineBadge(l.name, Mode.fromSofiaType(l.type), l.color, WHITE)
                    Spacer(Modifier.width(10.dp))
                    Text(Mode.fromSofiaType(l.type).label(), style = MaterialTheme.typography.titleSmall)
                }
                Spacer(Modifier.height(8.dp))
                val current = chosen[l.extId].orEmpty()
                // Each (line, last_stop) pair is a direction; short-turn trips show up as their own destination.
                val dests = rows.filter { it.extId == l.extId }.distinctBy { it.lastStop }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = current.isEmpty(), onClick = { chosen[l.extId] = emptySet() }, label = { Text("All directions") })
                    dests.forEach { d ->
                        FilterChip(
                            selected = d.lastStop in current,
                            onClick = { chosen[l.extId] = if (d.lastStop in current) current - d.lastStop else current + d.lastStop },
                            label = { Text(d.destination) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String, error: Boolean = false) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}
