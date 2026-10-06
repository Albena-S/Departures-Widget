package fr.departures.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import fr.departures.data.model.Entry
import fr.departures.data.model.Group
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer

/** Groups, entries and widget bindings, persisted as JSON in one DataStore. */
class GroupRepository(
    private val store: DataStore<Preferences>,
    /** Ids the system says are placed; bindings for anything else are stale and ignored. */
    private val liveWidgetIds: () -> Set<Int> = { emptySet() },
) {

    private val groupsKey = stringPreferencesKey("groups")
    private val bindingsKey = stringPreferencesKey("bindings")
    private val nextIdKey = longPreferencesKey("next_id")

    private val groupsSer = ListSerializer(Group.serializer())
    private val bindingsSer = MapSerializer(String.serializer(), Long.serializer())

    private fun Preferences.groups(): List<Group> =
        this[groupsKey]?.let { runCatching { AppJson.decodeFromString(groupsSer, it) }.getOrNull() }
            .orEmpty()
            .sortedBy { it.sortOrder }
            .map { g -> g.copy(entries = g.entries.sortedBy { it.sortOrder }) }

    private fun Preferences.allBindings(): Map<String, Long> =
        this[bindingsKey]?.let { runCatching { AppJson.decodeFromString(bindingsSer, it) }.getOrNull() }
            .orEmpty()

    private fun Preferences.bindings(): Map<String, Long> {
        val live = liveWidgetIds().map { it.toString() }.toSet()
        return allBindings().filterKeys { it in live }
    }

    private suspend fun editGroups(transform: (List<Group>, nextId: () -> Long) -> List<Group>) {
        store.edit { prefs ->
            var next = prefs[nextIdKey] ?: 1L
            val updated = transform(prefs.groups()) { next++ }
            prefs[groupsKey] = AppJson.encodeToString(groupsSer, renumber(updated))
            prefs[nextIdKey] = next
        }
    }

    private fun renumber(groups: List<Group>): List<Group> =
        groups.mapIndexed { i, g ->
            g.copy(sortOrder = i, entries = g.entries.mapIndexed { j, e -> e.copy(sortOrder = j, groupId = g.id) })
        }

    fun observeGroups(): Flow<List<Group>> = store.data.map { it.groups() }

    fun observeGroup(id: Long): Flow<Group?> = store.data.map { p -> p.groups().firstOrNull { it.id == id } }

    suspend fun getGroups(): List<Group> = store.data.first().groups()

    suspend fun getGroup(id: Long): Group? = getGroups().firstOrNull { it.id == id }

    suspend fun createGroup(title: String): Long {
        var created = 0L
        editGroups { list, nextId ->
            created = nextId()
            list + Group(id = created, title = title.trim(), sortOrder = list.size)
        }
        return created
    }

    suspend fun renameGroup(id: Long, title: String) = editGroups { list, _ ->
        list.map { if (it.id == id) it.copy(title = title.trim()) else it }
    }

    suspend fun deleteGroup(id: Long) = editGroups { list, _ -> list.filterNot { it.id == id } }

    suspend fun moveGroup(id: Long, toIndex: Int) = editGroups { list, _ -> list.move({ it.id == id }, toIndex) }

    suspend fun addEntries(groupId: Long, entries: List<Entry>) = editGroups { list, nextId ->
        list.map { g ->
            if (g.id != groupId) g
            else {
                // Same stop + line already present: merge directions instead of duplicating the row.
                val merged = g.entries.toMutableList()
                for (e in entries) {
                    val i = merged.indexOfFirst { it.stopRef == e.stopRef && it.lineRef == e.lineRef }
                    if (i >= 0) merged[i] = merged[i].copy(directions = mergeDirections(merged[i].directions, e.directions))
                    else merged += e.copy(id = nextId(), groupId = groupId)
                }
                g.copy(entries = merged)
            }
        }
    }

    suspend fun updateEntry(entry: Entry) = editGroups { list, _ ->
        list.map { g -> g.copy(entries = g.entries.map { if (it.id == entry.id) entry else it }) }
    }

    suspend fun removeEntry(id: Long) = editGroups { list, _ ->
        list.map { g -> g.copy(entries = g.entries.filterNot { it.id == id }) }
    }

    suspend fun moveEntry(id: Long, toIndex: Int) = editGroups { list, _ ->
        list.map { g -> if (g.entries.any { it.id == id }) g.copy(entries = g.entries.move({ it.id == id }, toIndex)) else g }
    }

    // --- widget bindings ---

    suspend fun bindWidget(appWidgetId: Int, groupId: Long) {
        store.edit { p -> p[bindingsKey] = AppJson.encodeToString(bindingsSer, p.allBindings() + (appWidgetId.toString() to groupId)) }
    }

    suspend fun unbindWidget(appWidgetId: Int) {
        store.edit { p -> p[bindingsKey] = AppJson.encodeToString(bindingsSer, p.allBindings() - appWidgetId.toString()) }
    }

    suspend fun groupIdForWidget(appWidgetId: Int): Long? = store.data.first().allBindings()[appWidgetId.toString()]

    suspend fun groupForWidget(appWidgetId: Int): Group? = groupIdForWidget(appWidgetId)?.let { getGroup(it) }

    suspend fun boundWidgetIds(): Set<Int> = store.data.first().bindings().keys.mapNotNull { it.toIntOrNull() }.toSet()

    /** Unique stops across every group shown by a placed widget. */
    suspend fun boundStopRefs(): Set<String> {
        val p = store.data.first()
        val ids = p.bindings().values.toSet()
        return p.groups().filter { it.id in ids }.flatMap { g -> g.entries.map { it.stopRef } }.toSet()
    }
}

private fun mergeDirections(a: Set<String>, b: Set<String>): Set<String> =
    if (a.isEmpty() || b.isEmpty()) emptySet() else a + b

private fun <T> List<T>.move(match: (T) -> Boolean, toIndex: Int): List<T> {
    val from = indexOfFirst(match)
    if (from < 0) return this
    val m = toMutableList()
    val item = m.removeAt(from)
    m.add(toIndex.coerceIn(0, m.size), item)
    return m
}
