package fr.departures

import android.content.Context
import fr.departures.data.DepartureRepository
import fr.departures.data.GroupRepository
import fr.departures.data.SettingsRepository
import fr.departures.data.api.OpenDataClient
import fr.departures.data.api.PrimClient
import fr.departures.data.api.createHttpClient
import fr.departures.data.departuresStore
import fr.departures.data.groupsStore
import fr.departures.data.settingsStore
import fr.departures.widget.liveWidgetIds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Manual DI: one instance per process, created by [App]. */
class ServiceLocator(context: Context) {
    val appContext: Context = context.applicationContext
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val http = createHttpClient()
    val groups = GroupRepository(appContext.groupsStore) { liveWidgetIds(appContext) }
    val settings = SettingsRepository(appContext.settingsStore)
    val prim = PrimClient(http)
    val openData = OpenDataClient(http)
    val departures = DepartureRepository(appContext.departuresStore, prim, settings, groups)
}

val Context.locator: ServiceLocator get() = (applicationContext as App).locator
