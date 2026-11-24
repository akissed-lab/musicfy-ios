# Navigation port

`ui/screens/Routes.kt` keeps every route string from upstream
`NavigationBuilder.kt` verbatim, including optional query arguments such as
`genre/{browseId}?params={params}` and `account_import/{service}?resync=…&then=…`.
Deep links and `navController.navigate(...)` calls therefore keep the same
contract as the Android app.

`ui/screens/NavigationBuilder.kt` registers all 66 concrete destinations with
Compose Multiplatform Navigation. Android-only builder inputs (`Activity`,
Hilt `hiltViewModel`, `MediaMetadataRetriever`, `TopAppBarScrollBehavior`,
Lottie) are gone; each destination currently renders a glass placeholder that
shows its route and resolved arguments. As each original screen is ported, its
`ported(...)` registration is replaced with the real composable.

`ui/component/AppNavigation.kt` replaces the Material3 `Scaffold`/bottom bar
with `AppNavHost` + `GlassNavigationBar`. The bar is visible on the four main
tabs and hidden on detail pages, matching the upstream behaviour. The whole
host is inside `GlassRoot`, so translucent chrome is inherited everywhere.

## Registered groups

- Main: home, search input, library, settings.
- Library: songs, playlists, added, local/cached shelves, imported sources,
  albums, artists.
- Settings: stats, cipher, playback, Musicfy, general, appearance,
  integrations, Discord, scrobbling, crossmix, developer space/playground,
  storage, experimental.
- Import/sync: providers, TuneMyMusic, progress, YouTube sync/login,
  account import.
- Audio: equalizer, gestures, audio lab/FX/quality.
- Modules: list, detail, sign-in.
- Content: browse, search results, album, artist and its subpages, online /
  local / auto / cached / top playlists, genre, YouTube browse.
