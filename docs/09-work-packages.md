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

Status: wave 1 of WP1-WP4 has landed (8 ViewModels, 20 UI-kit/util files,
9 settings/constants files, 15 player files). WP0 integrated the DI root,
composition locals, glass tab icons and iOS settings bridges. The integrator
is running a full `compileKotlinIosSimulatorArm64` over the combined batch.

Wave 2 candidates (not started): remaining local-only ViewModels, real main-tab
screens replacing the 66 placeholders, AVFoundation playback bridge (WP4
Stage 3) and provider/importer ports (WP5). A read-only reconnaissance of the
four main tabs is in progress to scope the screen package precisely.
