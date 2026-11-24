package com.example.musicfy.ui.screens

import androidx.compose.runtime.Immutable
import com.example.musicfy.resources.Res
import com.example.musicfy.resources.home
import com.example.musicfy.resources.search
import com.example.musicfy.resources.settings
import com.example.musicfy.resources.your_library
import org.jetbrains.compose.resources.StringResource

/**
 * Ported from Android `ui/screens/Screens.kt`.
 *
 * Android used @StringRes/@DrawableRes integers and R.raw Lottie icons.
 * Compose Multiplatform uses StringResource; icons are attached in the
 * navigation component once the shared icon set is ported.
 */
@Immutable
sealed class Screens(
    val title: StringResource,
    val route: String,
) {
    data object Home : Screens(title = Res.string.home, route = "home")
    data object Search : Screens(title = Res.string.search, route = "search_input")
    data object Library : Screens(title = Res.string.your_library, route = "library")
    data object Settings : Screens(title = Res.string.settings, route = "settings")

    companion object {
        val MainScreens = listOf(Home, Search, Library, Settings)
    }
}

enum class NavigationTab { HOME, SEARCH }

enum class DarkMode { AUTO, ON, OFF }

enum class LyricsPosition { LEFT, CENTER, RIGHT }

enum class OptionStats { WEEKS, MONTHS, YEARS, CONTINUOUS }
