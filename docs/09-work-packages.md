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

WP0 wires every new public composable into `NavigationBuilder.kt` and commits
after a green compile. Still outstanding: AVFoundation bridge (WP4 Stage 3),
Innertube pages/providers (WP5), importer, Discord/scrobble integrations and
the 62 secondary routes that remain glass placeholders. Home/Search/Library
shelves backed by YouTube currently render empty states with TODOs because the
Innertube page layer is not ported yet.
