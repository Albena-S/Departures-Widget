package fr.departures.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import fr.departures.locator
import fr.departures.ui.addentry.AddEntryFlow
import fr.departures.ui.groups.GroupEditorScreen
import fr.departures.ui.groups.GroupsScreen
import fr.departures.ui.groups.PickGroupScreen
import fr.departures.ui.onboarding.PermissionsScreen
import fr.departures.ui.settings.SettingsScreen
import kotlinx.coroutines.launch

object Routes {
    const val ONBOARDING = "onboarding"
    const val GROUPS = "groups"
    const val PICK = "pick"
    const val SETTINGS = "settings"
    const val EDITOR = "editor/{groupId}"
    const val ADD = "add/{groupId}"
    fun editor(id: Long) = "editor/$id"
    fun add(id: Long) = "add/$id"
}

/**
 * @param onGroupChosen set only in the widget configuration activity: called when the user
 *   picks a group or finishes creating one.
 */
@Composable
fun AppNav(
    start: String,
    onFinish: () -> Unit,
    onGroupChosen: ((Long) -> Unit)? = null,
    openSettingsSignal: Int = 0,
) {
    val nav = rememberNavController()
    LaunchedEffect(openSettingsSignal) {
        if (openSettingsSignal > 0) nav.navigate(Routes.SETTINGS) { launchSingleTop = true }
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val pickMode = onGroupChosen != null

    NavHost(navController = nav, startDestination = start) {
        composable(Routes.ONBOARDING) {
            PermissionsScreen(onContinue = {
                scope.launch { context.locator.settings.update { it.copy(onboardingDone = true) } }
                nav.navigate(if (pickMode) Routes.PICK else Routes.GROUPS) { popUpTo(Routes.ONBOARDING) { inclusive = true } }
            })
        }
        composable(Routes.GROUPS) {
            GroupsScreen(
                onOpen = { nav.navigate(Routes.editor(it)) },
                onSettings = { nav.navigate(Routes.SETTINGS) },
            )
        }
        composable(Routes.PICK) {
            PickGroupScreen(
                onPick = { onGroupChosen?.invoke(it) },
                onCreated = { nav.navigate(Routes.editor(it)) },
                onCancel = onFinish,
            )
        }
        composable(Routes.SETTINGS) {
            SettingsScreen(onBack = { if (!nav.popBackStack()) onFinish() })
        }
        composable(Routes.EDITOR, arguments = listOf(navArgument("groupId") { type = NavType.LongType })) { e ->
            val id = e.arguments!!.getLong("groupId")
            GroupEditorScreen(
                groupId = id,
                onBack = { if (!nav.popBackStack()) onFinish() },
                onAddEntry = { nav.navigate(Routes.add(id)) },
                onUseForWidget = onGroupChosen?.let { cb -> { cb(id) } },
            )
        }
        composable(Routes.ADD, arguments = listOf(navArgument("groupId") { type = NavType.LongType })) { e ->
            val id = e.arguments!!.getLong("groupId")
            AddEntryFlow(groupId = id, onDone = { nav.popBackStack() })
        }
    }
}
