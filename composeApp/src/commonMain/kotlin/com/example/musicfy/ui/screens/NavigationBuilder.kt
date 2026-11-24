package com.example.musicfy.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.example.musicfy.settings.SettingsKeys
import com.example.musicfy.settings.rememberPreference
import com.example.musicfy.ui.component.GlassCard
import com.example.musicfy.ui.screens.HomeScreen
import com.example.musicfy.ui.screens.artist.ArtistAlbumsScreen
import com.example.musicfy.ui.screens.artist.ArtistItemsScreen
import com.example.musicfy.ui.screens.artist.ArtistScreen
import com.example.musicfy.ui.screens.artist.ArtistSimilarScreen
import com.example.musicfy.ui.screens.artist.ArtistSongsScreen
import com.example.musicfy.ui.screens.library.LibraryHomeScreen
import com.example.musicfy.ui.screens.playlist.OnlinePlaylistScreen
import com.example.musicfy.ui.screens.search.GenreScreen
import com.example.musicfy.ui.screens.search.OnlineSearchResult
import com.example.musicfy.ui.screens.search.SearchScreen
import com.example.musicfy.ui.screens.settings.MusicfySettingsScreen
import com.example.musicfy.ui.screens.settings.ProfileScreen
import com.example.musicfy.ui.screens.settings.importsync.ImportSyncScreen
import com.example.musicfy.ui.theme.ForceDarkTheme

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
    composable(route = Screens.Home.route) {
        HomeScreen(navController = navController)
    }
    ported(Routes.SECTION_DETAIL, navController, stringArgs("sectionId"))
    ported(Routes.HISTORY, navController)
    ported(Routes.COMING_SOON, navController, stringArgs("sectionTitle"))
    composable(route = Screens.Search.route) {
        val pureBlack by rememberPreference(SettingsKeys.PureBlackKey, false)
        SearchScreen(navController = navController, pureBlack = pureBlack)
    }
    composable(route = Screens.Library.route) {
        val pureBlack by rememberPreference(SettingsKeys.PureBlackKey, false)
        LibraryHomeScreen(navController = navController, pureBlack = pureBlack)
    }

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
    composable(route = Screens.Settings.route) {
        ProfileScreen(navController = navController)
    }
    ported(Routes.STATS, navController)
    ported(Routes.CIPHER_SETTINGS, navController)
    ported(Routes.PLAYBACK_DIAGNOSTICS, navController)
    composable(route = Routes.MUSICFY_SETTINGS) {
        MusicfySettingsScreen(navController = navController)
    }
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
    composable(route = Routes.IMPORT_SYNC) {
        val pureBlack by rememberPreference(SettingsKeys.PureBlackKey, false)
        ImportSyncScreen(navController = navController, pureBlack = pureBlack)
    }
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
    composable(route = Routes.BROWSE, arguments = stringArgs("browseId")) { entry ->
        val browseId = entry.savedStateHandle.get<String>("browseId").orEmpty()
        val pureBlackSetting by rememberPreference(SettingsKeys.PureBlackKey, false)
        val pureBlack = pureBlackSetting && ForceDarkTheme
        BrowseScreen(navController, browseId, pureBlack)
    }
    composable(route = Routes.SEARCH_RESULTS, arguments = stringArgs("query")) { entry ->
        val query = entry.savedStateHandle.get<String>("query") ?: ""
        val pureBlack by rememberPreference(SettingsKeys.PureBlackKey, false)
        OnlineSearchResult(navController = navController, query = query, pureBlack = pureBlack)
    }
    composable(route = Routes.ALBUM, arguments = stringArgs("albumId")) { entry ->
        val albumId = entry.savedStateHandle.get<String>("albumId").orEmpty()
        val pureBlackSetting by rememberPreference(SettingsKeys.PureBlackKey, false)
        val pureBlack = pureBlackSetting && ForceDarkTheme
        AlbumScreen(navController, albumId, pureBlack)
    }
    composable(route = Routes.ARTIST, arguments = stringArgs("artistId")) { entry ->
        val artistId = entry.savedStateHandle.get<String>("artistId").orEmpty()
        val pureBlackSetting by rememberPreference(SettingsKeys.PureBlackKey, false)
        val pureBlack = pureBlackSetting && ForceDarkTheme
        ArtistScreen(navController, artistId, pureBlack)
    }
    composable(route = Routes.ARTIST_SONGS, arguments = stringArgs("artistId")) { entry ->
        val artistId = entry.savedStateHandle.get<String>("artistId").orEmpty()
        val pureBlackSetting by rememberPreference(SettingsKeys.PureBlackKey, false)
        val pureBlack = pureBlackSetting && ForceDarkTheme
        ArtistSongsScreen(navController, artistId, pureBlack)
    }
    composable(route = Routes.ARTIST_ALBUMS, arguments = stringArgs("artistId")) { entry ->
        val artistId = entry.savedStateHandle.get<String>("artistId").orEmpty()
        val pureBlackSetting by rememberPreference(SettingsKeys.PureBlackKey, false)
        val pureBlack = pureBlackSetting && ForceDarkTheme
        ArtistAlbumsScreen(navController, artistId, pureBlack)
    }
    composable(route = Routes.ARTIST_SIMILAR, arguments = stringArgs("artistId")) { entry ->
        val artistId = entry.savedStateHandle.get<String>("artistId").orEmpty()
        val pureBlackSetting by rememberPreference(SettingsKeys.PureBlackKey, false)
        val pureBlack = pureBlackSetting && ForceDarkTheme
        ArtistSimilarScreen(navController, artistId, pureBlack)
    }
    composable(
        route = Routes.ARTIST_ITEMS,
        arguments = listOf(
            navArgument("artistId") { type = NavType.StringType },
            navArgument("browseId") {
                type = NavType.StringType; nullable = true; defaultValue = null
            },
            navArgument("params") {
                type = NavType.StringType; nullable = true; defaultValue = null
            },
        ),
    ) { entry ->
        val artistId = entry.savedStateHandle.get<String>("artistId").orEmpty()
        val browseId = entry.savedStateHandle.get<String>("browseId").nullableArg()
        val params = entry.savedStateHandle.get<String>("params").nullableArg()
        val pureBlackSetting by rememberPreference(SettingsKeys.PureBlackKey, false)
        val pureBlack = pureBlackSetting && ForceDarkTheme
        ArtistItemsScreen(navController, artistId, browseId, params, pureBlack)
    }
    composable(route = Routes.ONLINE_PLAYLIST, arguments = stringArgs("playlistId")) { entry ->
        val playlistId = entry.savedStateHandle.get<String>("playlistId").orEmpty()
        val pureBlackSetting by rememberPreference(SettingsKeys.PureBlackKey, false)
        val pureBlack = pureBlackSetting && ForceDarkTheme
        OnlinePlaylistScreen(navController, playlistId, pureBlack)
    }
    ported(Routes.LOCAL_PLAYLIST, navController, stringArgs("playlistId"))
    ported(Routes.AUTO_PLAYLIST, navController, stringArgs("playlist"))
    ported(Routes.CACHE_PLAYLIST, navController, stringArgs("playlist"))
    ported(Routes.TOP_PLAYLIST, navController, stringArgs("top"))
    composable(
        route = Routes.GENRE,
        arguments = listOf(
            navArgument("browseId") { type = NavType.StringType },
            navArgument("params") {
                type = NavType.StringType; nullable = true; defaultValue = null
            },
        ),
    ) { entry ->
        val browseId = entry.savedStateHandle.get<String>("browseId").orEmpty()
        val params = entry.savedStateHandle.get<String>("params").nullableArg()
        val pureBlackSetting by rememberPreference(SettingsKeys.PureBlackKey, false)
        val pureBlack = pureBlackSetting && ForceDarkTheme
        GenreScreen(navController, browseId, params, pureBlack)
    }
    composable(
        route = Routes.YOUTUBE_BROWSE,
        arguments = listOf(
            navArgument("browseId") {
                type = NavType.StringType; nullable = true; defaultValue = null
            },
            navArgument("params") {
                type = NavType.StringType; nullable = true; defaultValue = null
            },
        ),
    ) { entry ->
        val browseId = entry.savedStateHandle.get<String>("browseId").nullableArg()
        val params = entry.savedStateHandle.get<String>("params").nullableArg()
        val pureBlackSetting by rememberPreference(SettingsKeys.PureBlackKey, false)
        val pureBlack = pureBlackSetting && ForceDarkTheme
        YouTubeBrowseScreen(navController, browseId, params, pureBlack)
    }
}

private fun stringArgs(name: String): List<NamedNavArgument> =
    listOf(navArgument(name) { type = NavType.StringType })

/** Navigation encodes missing/nullable args as either absent, empty or the literal "null". */
private fun String?.nullableArg(): String? =
    takeIf { !it.isNullOrEmpty() && it != "null" }

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
