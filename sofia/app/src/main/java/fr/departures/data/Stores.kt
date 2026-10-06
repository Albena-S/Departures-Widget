package fr.departures.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.serialization.json.Json

// One instance per file per process (DataStore requirement), hence top-level delegates.
val Context.groupsStore: DataStore<Preferences> by preferencesDataStore(name = "groups", corruptionHandler = onCorruption())
val Context.settingsStore: DataStore<Preferences> by preferencesDataStore(name = "settings", corruptionHandler = onCorruption())
val Context.departuresStore: DataStore<Preferences> by preferencesDataStore(name = "departures", corruptionHandler = onCorruption())

val AppJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    encodeDefaults = true
}

private fun onCorruption() = ReplaceFileCorruptionHandler<Preferences> { emptyPreferences() }
