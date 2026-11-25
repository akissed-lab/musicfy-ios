# Parallel work packages

Each agent owns a disjoint set of files. Agents must not edit outside their
package and must not run Gradle; the integrator compiles after a batch lands.

| WP | Owner | Read/write scope | Depends on |
| --- | --- | --- | --- |
| WP1 ViewModels | agent VM | `composeApp/src/commonMain/kotlin/com/example/musicfy/viewmodels/**` | facade, settings, providers |
| WP2 UI kit | agent UI | `composeApp/src/commonMain/kotlin/com/example/musicfy/ui/utils/**`, `ui/component/**` except `GlassKit.kt`, `AppNavigation.kt` | theme, glass |
| WP3 Settings | agent SET | `composeApp/src/commonMain/kotlin/com/example/musicfy/settings/**`, `constants/**` | none |
| WP4 Player | agent PLAYER | `composeApp/src/commonMain/kotlin/com/example/musicfy/player/**`, `composeApp/src/iosMain/kotlin/com/example/musicfy/player/**` | facade, settings |
| WP5 Providers | agent PROVIDERS | `composeApp/src/commonMain/kotlin/com/music/**`, `composeApp/src/commonMain/kotlin/com/example/musicfy/importer/**` | HTTP, settings |
| WP0 Integrator | main agent | `MusicfyApp.kt`, `ui/screens/**`, `sqldelight/**`, Gradle files, docs, `.github/**` | all |

Shared files that nobody except WP0 may touch: `GlassKit.kt`, `AppNavigation.kt`,
`NavigationBuilder.kt`, `Routes.kt`, `Music.sq`, `build.gradle.kts`,
`libs.versions.toml`, `MusicfyApp.kt`.

Status: wave 1 of WP1-WP4 has landed and the combined batch compiles green
(`compileKotlinIosSimulatorArm64`, commit `dfe8395`). WP0 integrated the DI
root, composition locals, glass tab icons, 34 new drawables, `Res.string.ok`,
the gacha sound files and iOS settings bridges.

Wave 2 has landed: 234 Kotlin files in `composeApp` (`verify.py`: 234/837,
warnings=0). All four main tabs, the settings screen, the player core and the
lyrics UI are now real glass screens; the remaining 62 destinations are still
glass placeholders. WP0 compiled, fixed the resulting import/resource/delegate
errors with the owning agents, and got a green `compileKotlinIosSimulatorArm64`
after four passes (last remaining error was a `getOrDefault` type inference in
`LocalSearchScreen`). Only non-fatal warnings remain (Skiko version skew,
expect/actual beta, deprecated `Instant`/date accessors).

Wave 2 parallel scope (all completed):

| Wave | Read/write scope | Content |
| --- | --- | --- |
| WP5a | `ui/screens/search/SearchDesign.kt`, `ui/component/SettingsHighlight.kt`, `SettingsGroup.kt`, `SubSettingsScaffold.kt`, `Preference.kt`, `scrobble/ScrobbleSite.kt` | shared glass screen primitives |
| WP1-2 | `viewmodels/**` | 7 more ViewModels incl. `HomeViewModel` state surface |
| WP5b | `ui/screens/settings/**` | real `MusicfySettingsScreen` |
| WP5c | `ui/screens/library/**` | real `LibraryHomeScreen` + nested helpers |
| WP5d | `ui/screens/search/**` except `SearchDesign.kt` | real `SearchScreen` + local search |
| WP4b | `ui/player/**` core files only | `BottomSheetPlayer`, `QueueScreen`, controls, styles |
| WP4c | `ui/player/**` lyrics files only | `LyricsScreen`, glow/stagger/readings, lyric sheets |

Wave 2 was committed as `a702094` after both `compileKotlinIosSimulatorArm64`
and `compileKotlinIosArm64` went green. Important finding during wave-3
planning: the iOS factory still returns `InMemoryPlaybackController`, so the
player UI advances without producing sound. Wave 3 therefore has two coupled
tracks: real YouTube Music search (Innertube search pages + `OnlineSearchVM` +
`OnlineSearchResult`) and real audio (Innertube `player` + `StreamResolver` +
AVFoundation). Spotify/Apple Music public-link import is wave 4.

Waves 3 and 4 landed after both `compileKotlinIosSimulatorArm64` and
`compileKotlinIosArm64` went green. `verify.py` now reports 274/837 ported files
with zero warnings.

| Agent | Scope | Content |
| --- | --- | --- |
| WP6a | `com/music/innertube/models/**`, `pages/**`, `utils/**` (new only) | search request/response models and renderer→`YTItem` parsers |
| WP6b | `com/music/innertube/YouTube.kt`, `SearchFilter.kt`, `YouTubeUrlParser.kt` | `search/summary/suggestions/continuation` transport and top result |
| WP6c | new `YouTubePlayer.kt`, `PlayerStreamModels.kt`, `YouTubeStreamResolver.kt` | direct audio URL → `ResolvedStream` |
| WP6d | `OnlineSearchViewModel.kt`, `OnlineSearchResult.kt`, `SearchScreen.kt`, suggestion VM | real result/filter/paging UI |
| WP6e | `iosMain/**/player/**` | AVFoundation engine replacing `InMemoryPlaybackController` |
| WP7 | `importer/**`, `ImportSyncViewModel`, `importsync/**`, SQLDelight facade | public Spotify/Apple links → metadata → YouTube match → local playlist |

Integration fixes needed for KMP: `kotlin.time.Clock` via `currentTimeMillis()`,
`Mutex` instead of JVM `synchronized`, `savedStateHandle.get<String>("query")`
instead of `Bundle.getString`, `Playlist` wrapper vs `PlaylistEntity`, explicit
`database.insert(...)`, and AVFoundation audio-session calls without an ObjC
error pointer.

Wave 5 replaced every remaining browse/detail placeholder after both
`compileKotlinIosSimulatorArm64` and `compileKotlinIosArm64` went green.
`verify.py` now reports 320/837 ported files with zero warnings.

| Agent | Scope | Content |
| --- | --- | --- |
| WP8a | `com/music/innertube/models/**`, `pages/**`, `YouTube.kt` | full `BrowseResponse`, renderers, `browse/album/artist/playlist/related` + continuations |
| WP8b | `viewmodels/**`, `db/**`, `NavigationBuilder.kt` | 9 detail VMs, `artistAlbumsPreview`, real route destinations |
| WP8c | `ui/screens/{album,artist,playlist,browse}/**`, `GenreScreen`, `YouTubeBrowseScreen` | 10 real glass screens reusing `PlaylistDetailScaffold` and detail primitives |

Integration fixes: all route args use `NavBackStackEntry.savedStateHandle.get<String>()`
(KMP navigation has no `Bundle.getString`); missing Compose `dp`/`Modifier`/`padding`
imports were added. CI additionally needed `macos-15-intel` (arm64 capacity
failures) and a 10 GB Gradle heap for the Kotlin/Native LTO link step. The
Xcode "Compile Kotlin" phase now reuses the already linked device framework
instead of starting a second, under-heaped Kotlin/Native link. The copy target
must use `$(CONFIGURATION)/$(SDK_NAME)`, matching `FRAMEWORK_SEARCH_PATHS`;
copying to a plain `iphoneos` folder makes Swift report `no such module 'ComposeApp'`.

Startup crash hardening: the iOS `PRAGMA journal_mode = WAL` call was removed
because it returns a result row that SQLDelight's `execute()` rejects;
AVAudioSession activation is deferred to the first play command and all
native setup is wrapped so a startup failure renders an error screen instead
of terminating the process.

Still outstanding: Discord/scrobble integrations, account-based imports,
DRM/FairPlay, radio continuation, ciphered/PoToken-only streams and the
secondary glass placeholders.
