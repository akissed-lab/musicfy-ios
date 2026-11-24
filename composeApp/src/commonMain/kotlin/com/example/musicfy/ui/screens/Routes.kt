package com.example.musicfy.ui.screens

/**
 * Every route registered by upstream `NavigationBuilder.kt`, kept verbatim so
 * deep links and the Kotlin navigation graph match the Android app. The ported
 * screens are filled in behind these routes; the route names themselves are
 * part of the app's contract and must not be renamed.
 */
object Routes {
    const val SECTION_DETAIL = "section_detail/{sectionId}"
    const val HISTORY = "history"
    const val COMING_SOON = "coming_soon/{sectionTitle}"

    const val LIBRARY_SONGS = "library/songs"
    const val LIBRARY_PLAYLISTS = "library/playlists"
    const val LIBRARY_ADDED = "library/added"
    const val LIBRARY_ALBUMS = "library/albums"
    const val LIBRARY_ARTISTS = "library/artists"
    const val LIBRARY_IMPORTED = "library/imported/{source}"
    const val LIBRARY_IMPORTED_PLAYLISTS = "library/imported/{source}/playlists"

    const val STATS = "stats"
    const val CIPHER_SETTINGS = "cipher_settings"
    const val PLAYBACK_DIAGNOSTICS = "playback_diagnostics"
    const val MUSICFY_SETTINGS = "musicfy_settings"
    const val GENERAL_SETTINGS = "general_settings"
    const val APPEARANCE_SETTINGS = "appearance_settings"
    const val INTEGRATIONS = "integrations_settings"
    const val DISCORD = "integrations_settings/discord"
    const val SCROBBLE = "integrations_settings/scrobble/{site}"
    const val PLAYBACK_SETTINGS = "playback_settings"
    const val CROSSMIX_SETTINGS = "crossmix_settings"
    const val DEVELOPER_SPACE = "developer_space?person={person}"
    const val DEVELOPER_PLAYGROUND = "developer_playground"
    const val STORAGE = "storage_settings"
    const val EXPERIMENTAL_SETTINGS = "experimental_settings"

    const val IMPORT_SYNC = "import_sync"
    const val IMPORT_PROVIDERS = "import_providers"
    const val IMPORT_TUNE_MY_MUSIC = "import_tunemymusic"
    const val IMPORT_PROGRESS = "import_progress"
    const val YOUTUBE_SYNC = "youtube_sync"
    const val ACCOUNT_IMPORT = "account_import/{service}?resync={resync}&then={then}"
    const val YOUTUBE_LOGIN = "youtube_login"

    const val EQUALIZER = "equalizer"
    const val GESTURE_SETTINGS = "gesture_settings"
    const val AUDIO_LAB = "audio_lab"
    const val AUDIO_FX = "audio_fx"
    const val AUDIO_QUALITY = "audio_quality"

    const val MODULES = "modules"
    const val MODULE = "module/{id}"
    const val MODULE_SIGN_IN = "module_signin/{id}"

    const val BROWSE = "browse/{browseId}"
    const val SEARCH_RESULTS = "search/{query}"
    const val ALBUM = "album/{albumId}"
    const val ARTIST = "artist/{artistId}"
    const val ARTIST_SONGS = "artist/{artistId}/songs"
    const val ARTIST_ALBUMS = "artist/{artistId}/albums"
    const val ARTIST_SIMILAR = "artist/{artistId}/similar"
    const val ARTIST_ITEMS = "artist/{artistId}/items?browseId={browseId}?params={params}"

    const val ONLINE_PLAYLIST = "online_playlist/{playlistId}"
    const val LOCAL_PLAYLIST = "local_playlist/{playlistId}"
    const val AUTO_PLAYLIST = "auto_playlist/{playlist}"
    const val CACHE_PLAYLIST = "cache_playlist/{playlist}"
    const val TOP_PLAYLIST = "top_playlist/{top}"
    const val GENRE = "genre/{browseId}?params={params}"
    const val YOUTUBE_BROWSE = "youtube_browse/{browseId}?params={params}"

    val LibraryShelfKeys = listOf("local", "cached")

    /** Routes that own one of the four main tabs; the glass nav pill stays visible on them. */
    val MainRoutes = setOf(Screens.Home.route, Screens.Search.route, Screens.Library.route, Screens.Settings.route)
}
