package com.example.musicfy.ui.screens

import androidx.compose.runtime.Immutable
import com.example.musicfy.resources.Res
import com.example.musicfy.resources.home
import com.example.musicfy.resources.home_filled
import com.example.musicfy.resources.lib
import com.example.musicfy.resources.lib_fill
import com.example.musicfy.resources.search
import com.example.musicfy.resources.search_filled
import com.example.musicfy.resources.settings
import com.example.musicfy.resources.settings_outline
import com.example.musicfy.resources.settings_filled
import com.example.musicfy.resources.your_library
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.StringResource

/**
 * Ported from Android `ui/screens/Screens.kt`.
 *
 * Android used @StringRes/@DrawableRes integers and R.raw Lottie icons.
 * Compose Multiplatform uses StringResource/DrawableResource; the tab icons are
 * the SVG/XML set copied from upstream and rendered in [GlassNavigationBar].
 */
@Immutable
sealed class Screens(
    val title: StringResource,
    val route: String,
    val icon: DrawableResource,
    val selectedIcon: DrawableResource,
) {
    data object Home : Screens(
        title = Res.string.home,
        route = "home",
        icon = Res.drawable.home,
        selectedIcon = Res.drawable.home_filled,
    )

    data object Search : Screens(
        title = Res.string.search,
        route = "search_input",
        icon = Res.drawable.search,
        selectedIcon = Res.drawable.search_filled,
    )

    data object Library : Screens(
        title = Res.string.your_library,
        route = "library",
        icon = Res.drawable.lib,
        selectedIcon = Res.drawable.lib_fill,
    )

    data object Settings : Screens(
        title = Res.string.settings,
        route = "settings",
        icon = Res.drawable.settings_outline,
        selectedIcon = Res.drawable.settings_filled,
    )

    companion object {
        val MainScreens = listOf(Home, Search, Library, Settings)
    }
}

enum class NavigationTab { HOME, SEARCH }

enum class DarkMode { AUTO, ON, OFF }

enum class LyricsPosition { LEFT, CENTER, RIGHT }

enum class OptionStats { WEEKS, MONTHS, YEARS, CONTINUOUS }
