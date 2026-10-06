package fr.departures.data

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import fr.departures.BuildConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

enum class ThemePref { LIGHT, DARK }

data class Settings(
    val theme: ThemePref = ThemePref.LIGHT,
    val refreshIntervalSec: Int = 30,
    val fakeData: Boolean = false,
    val onboardingDone: Boolean = false,
)

val REFRESH_INTERVALS = listOf(20, 30, 60)

class SettingsRepository(private val store: DataStore<Preferences>) {
    private val themeKey = stringPreferencesKey("theme")
    private val intervalKey = intPreferencesKey("refresh_interval_sec")
    private val fakeKey = booleanPreferencesKey("fake_data")
    private val onboardingKey = booleanPreferencesKey("onboarding_done")

    val settings: Flow<Settings> = store.data.map { p ->
        Settings(
            theme = p[themeKey]?.let { runCatching { ThemePref.valueOf(it) }.getOrNull() } ?: ThemePref.LIGHT,
            refreshIntervalSec = p[intervalKey]?.takeIf { it in REFRESH_INTERVALS } ?: 30,
            fakeData = BuildConfig.DEBUG && (p[fakeKey] ?: false),
            onboardingDone = p[onboardingKey] ?: false,
        )
    }

    suspend fun current(): Settings = settings.first()

    suspend fun update(transform: (Settings) -> Settings) {
        store.edit { p ->
            val s = transform(
                Settings(
                    theme = p[themeKey]?.let { runCatching { ThemePref.valueOf(it) }.getOrNull() } ?: ThemePref.LIGHT,
                    refreshIntervalSec = p[intervalKey] ?: 30,
                    fakeData = p[fakeKey] ?: false,
                    onboardingDone = p[onboardingKey] ?: false,
                )
            )
            p[themeKey] = s.theme.name
            p[intervalKey] = s.refreshIntervalSec
            p[fakeKey] = s.fakeData
            p[onboardingKey] = s.onboardingDone
        }
    }

}
