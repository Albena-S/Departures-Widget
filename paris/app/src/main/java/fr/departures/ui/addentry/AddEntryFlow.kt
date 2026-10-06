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
import fr.departures.data.FetchResult
import fr.departures.data.api.LineAtStation
import fr.departures.data.api.StationHit
import fr.departures.data.api.lineRefFor
import fr.departures.data.api.monitoringRefFor
import fr.departures.data.model.Entry
import fr.departures.data.model.lineCode
import fr.departures.locator
import fr.departures.ui.LineBadge
import fr.departures.ui.label
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private enum class Step { SEARCH, LINES, DIRECTIONS }

/** Live directions lookup result for the picked station. */
private sealed interface Live {
    data object Loading : Live
    data class Ok(val byLine: Map<String, List<String>>) : Live
    data class Failed(val message: String) : Live
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEntryFlow(groupId: Long, onDone: () -> Unit) {
    val context = LocalContext.current
    val loc = context.locator
    val scope = rememberCoroutineScope()

    var step by remember { mutableStateOf(Step.SEARCH) }
    var query by remember { mutableStateOf("") }
    var results by remember { mutableStateOf<List<StationHit>>(emptyList()) }
    var searching by remember { mutableStateOf(false) }
    var searchError by remember { mutableStateOf<String?>(null) }

    var station by remember { mutableStateOf<StationHit?>(null) }
    var lines by remember { mutableStateOf<List<LineAtStation>?>(null) }
    var linesError by remember { mutableStateOf<String?>(null) }
    val selectedLines = remember { mutableStateMapOf<String, Boolean>() }

    var live by remember { mutableStateOf<Live>(Live.Loading) }
    val chosenDirs = remember { mutableStateMapOf<String, Set<String>>() } // empty = all

    BackHandler(enabled = step != Step.SEARCH) {
        step = if (step == Step.DIRECTIONS) Step.LINES else Step.SEARCH
    }

    // 1. debounced station search
    LaunchedEffect(query) {
        if (query.trim().length < 2) { results = emptyList(); searchError = null; return@LaunchedEffect }
        delay(300)
        searching = true
        runCatching { loc.openData.searchStations(query) }
            .onSuccess { results = it; searchError = null }
            .onFailure { searchError = "Search failed. Check your connection." }
        searching = false
    }
    // 2. lines at the station
    LaunchedEffect(station) {
        val s = station ?: return@LaunchedEffect
        lines = null; linesError = null; selectedLines.clear(); chosenDirs.clear()
        runCatching { loc.openData.linesAt(s.stopId) }
            .onSuccess { lines = it }
            .onFailure { linesError = "Couldn't load lines. Check your connection." }
    }
    // 3. live call for directions (one request for the whole station)
    LaunchedEffect(step) {
        if (step != Step.DIRECTIONS) return@LaunchedEffect
        val s = station ?: return@LaunchedEffect
        live = Live.Loading
        val key = loc.settings.effectiveApiKey()
        live = when (val r = loc.prim.stopMonitoring(monitoringRefFor(s.stopId), key)) {
            is FetchResult.Success -> Live.Ok(
                r.list.groupBy { lineCode(it.lineRef) }
                    .mapValues { (_, deps) -> deps.map { it.destination }.filter { it.isNotBlank() }.distinct().sorted() }
            )
            FetchResult.Unauthorized -> Live.Failed(if (key.isEmpty()) "No API key yet. Add it in Settings." else "The API key was refused. Check it in Settings.")
            FetchResult.RateLimited -> Live.Failed("Rate limit reached. Try again in a minute.")
            is FetchResult.Failure -> Live.Failed("Couldn't reach IDFM. Check your connection.")
        }
    }

    val picked = lines.orEmpty().filter { selectedLines[it.lineId] == true }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        when (step) {
                            Step.SEARCH -> "Find a station"
                            Step.LINES -> station?.name ?: "Lines"
                            Step.DIRECTIONS -> "Directions"
                        }
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
            when (step) {
                Step.LINES -> Button(
                    onClick = { step = Step.DIRECTIONS },
                    enabled = picked.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                ) { Text(if (picked.isEmpty()) "Pick at least one line" else "Next") }
                Step.DIRECTIONS -> Button(
                    onClick = {
                        val s = station ?: return@Button
                        scope.launch {
                            loc.groups.addEntries(groupId, picked.map { l ->
                                Entry(
                                    stopRef = monitoringRefFor(s.stopId),
                                    stopName = s.name,
                                    lineRef = lineRefFor(l.lineId),
                                    lineShortName = l.shortName,
                                    mode = l.mode,
                                    lineColor = l.color,
                                    lineTextColor = l.textColor,
                                    directions = chosenDirs[l.lineId].orEmpty(),
                                )
                            })
                            onDone()
                        }
                    },
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp),
                ) { Text("Add to group") }
                else -> Unit
            }
        },
    ) { pad ->
        Box(Modifier.padding(pad).fillMaxSize()) {
            when (step) {
                Step.SEARCH -> SearchStep(query, { query = it }, results, searching, searchError) {
                    station = it; step = Step.LINES
                }
                Step.LINES -> LinesStep(lines, linesError, selectedLines)
                Step.DIRECTIONS -> DirectionsStep(picked, live, chosenDirs)
            }
        }
    }
}

@Composable
private fun SearchStep(
    query: String, onQuery: (String) -> Unit,
    results: List<StationHit>, searching: Boolean, error: String?,
    onPick: (StationHit) -> Unit,
) {
    Column(Modifier.fillMaxSize().imePadding()) {
        OutlinedTextField(
            value = query,
            onValueChange = onQuery,
            leadingIcon = { Icon(Icons.Filled.Search, null) },
            trailingIcon = { if (searching) CircularProgressIndicator(Modifier.padding(12.dp).width(18.dp).height(18.dp), strokeWidth = 2.dp) },
            placeholder = { Text("Station name, e.g. Cernay") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        )
        error?.let { Hint(it) }
        if (!searching && error == null && query.trim().length >= 2 && results.isEmpty()) Hint("No station found for “${query.trim()}”.")
        LazyColumn(contentPadding = PaddingValues(bottom = 24.dp)) {
            items(results, key = { it.stopId }) { s ->
                Column(
                    Modifier.fillMaxWidth().clickable { onPick(s) }.padding(horizontal = 16.dp, vertical = 12.dp)
                ) {
                    Text(s.name, style = MaterialTheme.typography.titleMedium)
                    Text(
                        listOf(
                            s.commune,
                            s.modes.sortedBy { it.ordinal }.joinToString(" · ") { it.label() },
                            s.lines.take(8).joinToString(", ") + if (s.lines.size > 8) "…" else "",
                        )
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
private fun LinesStep(lines: List<LineAtStation>?, error: String?, selected: MutableMap<String, Boolean>) {
    when {
        error != null -> Hint(error)
        lines == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        lines.isEmpty() -> Hint("No lines found at this station.")
        else -> LazyColumn(contentPadding = PaddingValues(vertical = 8.dp)) {
            item { Hint("Pick the lines you take from here.") }
            items(lines, key = { it.lineId }) { l ->
                val on = selected[l.lineId] == true
                Row(
                    Modifier.fillMaxWidth().clickable { selected[l.lineId] = !on }.padding(horizontal = 16.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    LineBadge(l.shortName, l.mode, l.color, l.textColor, heightDp = 26f)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(l.longName.ifBlank { l.shortName }, style = MaterialTheme.typography.bodyLarge, maxLines = 1)
                        Text(l.mode.label(), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Checkbox(checked = on, onCheckedChange = { selected[l.lineId] = it })
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun DirectionsStep(picked: List<LineAtStation>, live: Live, chosen: MutableMap<String, Set<String>>) {
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(20.dp)) {
        if (live is Live.Failed) item { Text(live.message, color = MaterialTheme.colorScheme.error) }
        items(picked, key = { it.lineId }) { l ->
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    LineBadge(l.shortName, l.mode, l.color, l.textColor)
                    Spacer(Modifier.width(10.dp))
                    Text(l.longName.ifBlank { l.mode.label() }, style = MaterialTheme.typography.titleSmall, maxLines = 1)
                }
                Spacer(Modifier.height(8.dp))
                val current = chosen[l.lineId].orEmpty()
                val names = ((live as? Live.Ok)?.byLine?.get(l.lineId).orEmpty() + current).distinct()
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = current.isEmpty(),
                        onClick = { chosen[l.lineId] = emptySet() },
                        label = { Text("All directions") },
                    )
                    names.forEach { n ->
                        FilterChip(
                            selected = n in current,
                            onClick = { chosen[l.lineId] = if (n in current) current - n else current + n },
                            label = { Text(n) },
                        )
                    }
                }
                when {
                    live is Live.Loading -> Text("Checking live departures…", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    live is Live.Ok && live.byLine[l.lineId].isNullOrEmpty() -> Column {
                        Text("No live data for this line at this stop", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
                        Text(
                            "No departures right now, so directions can't be listed. You can still pick All directions.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    else -> Unit
                }
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

