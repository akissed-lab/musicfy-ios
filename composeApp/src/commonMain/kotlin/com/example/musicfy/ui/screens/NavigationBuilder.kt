package com.example.musicfy.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.musicfy.ui.component.GlassCard

/**
 * KMP port of upstream `ui/screens/NavigationBuilder.kt`.
 *
 * Android-only inputs (Activity, Hilt, MediaMetadataRetriever, Lottie and
 * Material3 scroll behaviour) are deliberately absent. The route table,
 * argument names and deep-link shapes are identical; each destination now
 * renders through the shared glass shell until its original screen is ported.
 */
fun NavGraphBuilder.navigationBuilder(navController: NavHostController) {
    // ---- main tabs -----------------------------------------------------------------------------
    ported(Screens.Home.route, navController)
    ported(Routes.SECTION_DETAIL, navController, stringArgs("sectionId"))
    ported(Routes.HISTORY, navController)
    ported(Routes.COMING_SOON, navController, stringArgs("sectionTitle"))
    ported(Screens.Search.route, navController)
    ported(Screens.Library.route, navController)

    // ---- library --------------------------------------------------------------------------------
    ported(Routes.LIBRARY_SONGS, navController)
    ported(Routes.LIBRARY_PLAYLISTS, navController)
    ported(Routes.LIBRARY_ADDED, navController)
    Routes.LibraryShelfKeys.forEach { shelf ->
        ported("library/$shelf/added", navController)
        ported("library/$shelf/albums", navController)
        ported("library/$shelf/artists", navController)
        ported("library/$shelf/playlists", navController)
    }
    ported(Routes.LIBRARY_IMPORTED, navController, stringArgs("source"))
    ported(Routes.LIBRARY_IMPORTED_PLAYLISTS, navController, stringArgs("source"))
    ported(Routes.LIBRARY_ALBUMS, navController)
    ported(Routes.LIBRARY_ARTISTS, navController)

    // ---- settings -------------------------------------------------------------------------------
    ported(Screens.Settings.route, navController)
    ported(Routes.STATS, navController)
    ported(Routes.CIPHER_SETTINGS, navController)
    ported(Routes.PLAYBACK_DIAGNOSTICS, navController)
    ported(Routes.MUSICFY_SETTINGS, navController)
    ported(Routes.GENERAL_SETTINGS, navController)
    ported(Routes.APPEARANCE_SETTINGS, navController)
    ported(Routes.INTEGRATIONS, navController)
    ported(Routes.DISCORD, navController)
    ported(Routes.SCROBBLE, navController, stringArgs("site"))
    ported(Routes.PLAYBACK_SETTINGS, navController)
    ported(Routes.CROSSMIX_SETTINGS, navController)
    ported(
        Routes.DEVELOPER_SPACE,
        navController,
        listOf(navArgument("person") { type = NavType.IntType; defaultValue = 0 }),
    )
    ported(Routes.DEVELOPER_PLAYGROUND, navController)
    ported(Routes.STORAGE, navController)
    ported(Routes.EXPERIMENTAL_SETTINGS, navController)

    // ---- import / sync --------------------------------------------------------------------------
    ported(Routes.IMPORT_SYNC, navController)
    ported(Routes.IMPORT_PROVIDERS, navController)
    ported(Routes.IMPORT_TUNE_MY_MUSIC, navController)
    ported(Routes.IMPORT_PROGRESS, navController)
    ported(Routes.YOUTUBE_SYNC, navController)
    ported(
        Routes.ACCOUNT_IMPORT,
        navController,
        listOf(
            navArgument("service") { type = NavType.StringType },
            navArgument("resync") { type = NavType.StringType; nullable = true; defaultValue = null },
            navArgument("then") { type = NavType.StringType; nullable = true; defaultValue = null },
        ),
    )
    ported(Routes.YOUTUBE_LOGIN, navController)

    // ---- audio settings -------------------------------------------------------------------------
    ported(Routes.EQUALIZER, navController)
    ported(Routes.GESTURE_SETTINGS, navController)
    ported(Routes.AUDIO_LAB, navController)
    ported(Routes.AUDIO_FX, navController)
    ported(Routes.AUDIO_QUALITY, navController)

    // ---- modules --------------------------------------------------------------------------------
    ported(Routes.MODULES, navController)
    ported(Routes.MODULE, navController, stringArgs("id"))
    ported(Routes.MODULE_SIGN_IN, navController, stringArgs("id"))

    // ---- browse, search and detail pages --------------------------------------------------------
    ported(Routes.BROWSE, navController, stringArgs("browseId"))
    ported(Routes.SEARCH_RESULTS, navController, stringArgs("query"))
    ported(Routes.ALBUM, navController, stringArgs("albumId"))
    ported(Routes.ARTIST, navController, stringArgs("artistId"))
    ported(Routes.ARTIST_SONGS, navController, stringArgs("artistId"))
    ported(Routes.ARTIST_ALBUMS, navController, stringArgs("artistId"))
    ported(Routes.ARTIST_SIMILAR, navController, stringArgs("artistId"))
    ported(
        Routes.ARTIST_ITEMS,
        navController,
        listOf(
            navArgument("artistId") { type = NavType.StringType },
            navArgument("browseId") { type = NavType.StringType; nullable = true },
            navArgument("params") { type = NavType.StringType; nullable = true },
        ),
    )
    ported(Routes.ONLINE_PLAYLIST, navController, stringArgs("playlistId"))
    ported(Routes.LOCAL_PLAYLIST, navController, stringArgs("playlistId"))
    ported(Routes.AUTO_PLAYLIST, navController, stringArgs("playlist"))
    ported(Routes.CACHE_PLAYLIST, navController, stringArgs("playlist"))
    ported(Routes.TOP_PLAYLIST, navController, stringArgs("top"))
    ported(
        Routes.GENRE,
        navController,
        listOf(
            navArgument("browseId") { type = NavType.StringType },
            navArgument("params") { type = NavType.StringType; nullable = true },
        ),
    )
    ported(
        Routes.YOUTUBE_BROWSE,
        navController,
        listOf(
            navArgument("browseId") { type = NavType.StringType; nullable = true },
            navArgument("params") { type = NavType.StringType; nullable = true },
        ),
    )
}

private fun stringArgs(name: String): List<NamedNavArgument> =
    listOf(navArgument(name) { type = NavType.StringType })

private fun NavGraphBuilder.ported(
    route: String,
    navController: NavHostController,
    arguments: List<NamedNavArgument> = emptyList(),
) {
    composable(route = route, arguments = arguments) {
        PortedRoute(route)
    }
}

/** Temporary glass destination; replaced by the original screen file as each screen is ported. */
@Composable
private fun PortedRoute(route: String) {
    GlassCard(modifier = Modifier.fillMaxSize()) {
        Text(text = route)
    }
}
