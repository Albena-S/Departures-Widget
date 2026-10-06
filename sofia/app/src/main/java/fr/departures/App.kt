package fr.departures

import android.app.Application
import fr.departures.data.stops.SofiaStopRepository
import fr.departures.widget.updateAllWidgets
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class App : Application() {
    val locator: ServiceLocator by lazy { ServiceLocator(this) }

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        // Monthly refresh of the Sofia stop list (also fetches it if missing).
        locator.scope.launch { locator.stops.refreshIfOlderThan(SofiaStopRepository.MONTH_MS) }
        // Group edits and theme changes show up on placed widgets without waiting for a tick.
        locator.scope.launch {
            combine(locator.groups.observeGroups(), locator.settings.settings) { g, s -> g to s.theme }
                .distinctUntilChanged()
                .drop(1)
                .debounce(400)
                .collect { runCatching { updateAllWidgets(this@App) } }
        }
    }
}
