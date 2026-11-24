package com.example.musicfy.ui.component

import androidx.compose.runtime.Stable
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController
import androidx.navigation.NavGraph.Companion.findStartDestination
import com.example.musicfy.ui.screens.Routes
import com.example.musicfy.ui.screens.Screens

/**
 * Extracted from upstream `AppNavigation.kt` so screens and ported components
 * can navigate to a main tab without editing `AppNavigation.kt`. The route
 * names stay the same as the Android app and the ported [Routes] graph.
 */

/** Enter a main tab as a tab, not as a child of whichever tab was on screen. */
fun NavController.navigateToTab(route: String) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) {
            saveState = true
        }
        launchSingleTop = true
        restoreState = true
    }
}

/** Handle a tab tap: switch tabs, return to the tab page, or scroll to top. */
fun NavController.onTabTapped(
    screen: Screens,
    isSelectedTab: Boolean,
    scrollToTop: () -> Unit,
) {
    if (!isSelectedTab) {
        navigateToTab(screen.route)
        return
    }
    if (currentDestination?.route == screen.route) {
        currentBackStackEntry?.savedStateHandle?.set("scrollToTop", true)
        scrollToTop()
        return
    }
    if (!popBackStack(screen.route, inclusive = false)) navigateToTab(screen.route)
}

/** Whether [currentRoute] belongs to [screenRoute]. */
@Stable
fun isRouteSelected(
    currentRoute: String?,
    screenRoute: String,
    navigationItems: List<Screens> = Screens.MainScreens,
): Boolean {
    if (currentRoute == null) return false
    if (currentRoute == screenRoute) return true
    return navigationItems.any { it.route == screenRoute } &&
        currentRoute.startsWith("$screenRoute/")
}

/** Sub-screens owned by one of the four tabs, copied from upstream `RouteOwners`. */
val RouteOwners: List<Pair<String, String>> = listOf(
    "search/" to Screens.Search.route,
    "genre/" to Screens.Search.route,
    "youtube_browse/" to Screens.Search.route,
    "browse/" to Screens.Search.route,
    Routes.LIBRARY_SONGS to Screens.Library.route,
    "library/" to Screens.Library.route,
    Routes.CIPHER_SETTINGS to Screens.Settings.route,
    Routes.PLAYBACK_DIAGNOSTICS to Screens.Settings.route,
    Routes.MUSICFY_SETTINGS to Screens.Settings.route,
    Routes.GENERAL_SETTINGS to Screens.Settings.route,
    Routes.APPEARANCE_SETTINGS to Screens.Settings.route,
    Routes.PLAYBACK_SETTINGS to Screens.Settings.route,
    Routes.EXPERIMENTAL_SETTINGS to Screens.Settings.route,
    Routes.DEVELOPER_SPACE to Screens.Settings.route,
    Routes.DEVELOPER_PLAYGROUND to Screens.Settings.route,
    Routes.STORAGE to Screens.Settings.route,
    Routes.IMPORT_SYNC to Screens.Settings.route,
    Routes.IMPORT_PROVIDERS to Screens.Settings.route,
    Routes.IMPORT_TUNE_MY_MUSIC to Screens.Settings.route,
    Routes.IMPORT_PROGRESS to Screens.Settings.route,
    Routes.YOUTUBE_SYNC to Screens.Settings.route,
    Routes.YOUTUBE_LOGIN to Screens.Settings.route,
    "account_import/" to Screens.Settings.route,
    Routes.EQUALIZER to Screens.Settings.route,
)

/** Which tab owns a route that is not itself a tab page. */
fun owningTabRoute(
    currentRoute: String?,
    navigationItems: List<Screens> = Screens.MainScreens,
): String? {
    if (currentRoute == null) return null
    navigationItems.firstOrNull { isRouteSelected(currentRoute, it.route, navigationItems) }
        ?.let { return it.route }
    RouteOwners.firstOrNull { (prefix, _) ->
        currentRoute == prefix || currentRoute.startsWith(prefix)
    }?.let { return it.second }
    if (currentRoute.endsWith("_settings") || currentRoute.startsWith(Screens.Settings.route)) {
        return Screens.Settings.route
    }
    return null
}

/** Tab that owns the page on screen, preferring the tab entry in the back stack. */
fun owningTab(
    backStack: List<NavBackStackEntry>,
    navigationItems: List<Screens> = Screens.MainScreens,
): String {
    val tabRoutes = navigationItems.mapTo(HashSet()) { it.route }
    backStack.asReversed().firstOrNull { it.destination.route in tabRoutes }
        ?.destination?.route
        ?.let { return it }
    return owningTabRoute(backStack.lastOrNull()?.destination?.route, navigationItems)
        ?: navigationItems.firstOrNull()?.route.orEmpty()
}

/** Per-entry tab ownership used by tab transitions. */
class TabOwnership(private val navigationItems: List<Screens> = Screens.MainScreens) {
    private val tabRoutes = navigationItems.map { it.route }
    private val owners = HashMap<String, String>()

    fun update(backStack: List<NavBackStackEntry>) {
        var owner: String? = null
        backStack.forEach { entry ->
            val route = entry.destination.route
            if (route != null && route in tabRoutes) owner = route
            owner?.let { owners[entry.id] = it }
        }
        if (owners.size > 400) {
            val live = backStack.mapTo(HashSet()) { it.id }
            owners.keys.retainAll(live)
        }
    }

    /** Tab position in the bar, or -1 when nothing places this entry. */
    fun indexOf(entry: NavBackStackEntry, backStack: List<NavBackStackEntry>): Int {
        update(backStack)
        val owner = owners[entry.id]
            ?: entry.destination.route?.takeIf { it in tabRoutes }
            ?: owningTabRoute(entry.destination.route, navigationItems)
        return tabRoutes.indexOf(owner)
    }
}
