# Architecture mapping (Android → iOS)

## Build system

| Android | iOS port |
|---|---|
| Gradle multi-module: `:app`, 13 `:providers:*`, 3 `:modules:*` | One `:composeApp` KMP module; iOS targets `iosX64`, `iosArm64`, `iosSimulatorArm64`. Provider targets can be split later if needed. |
| Kotlin 2.3.10, Compose 1.10.2 | Kotlin 2.3.10, Compose Multiplatform 1.12.2 |
| Hilt/Dagger 2.59.1 | Explicit constructors + `remember`/`CompositionLocal`; Hilt entry points are replaced by expect/actual platform bridges |
| Room 2.8.4, DataStore 1.2.0 | SQLDelight 2.4.1 (`NativeSqliteDriver`), NSUserDefaults behind `SettingsStore` |
| Media3/ExoPlayer 1.7.1 | To be implemented with AVFoundation (via `iosMain` bridges); Compose UI stays common |
| Ktor 3.4.0 + kotlinx.serialization | Same Ktor/CMP-compatible artifacts; Darwin engine on iOS |
| Coil 3, Lottie, uCrop, WorkManager, WebView | Async image loading and native bridges are stage-4/5 items |

## Source analysis summary

- 837 reference Kotlin files, ~186k lines total.
- `app` UI: 377 files / ~113.8k lines (67% of the Android codebase).
- Largest subsystems: `ui/screens` 49.4k, `ui/player` 31.4k, `ui/component` 23.7k,
  `playback` 10.7k, `viewmodels` 7.6k, `db` 3.9k.
- The player is a Compose overlay above the navigation graph, not a route. Its
  state comes from `PlayerConnection` flows; on iOS those become a shared
  state holder updated from `iosMain` AVPlayer observers.

## Data layer

Android `InternalDatabase` v41 (23 entities, 3 views) is mirrored in
`commonMain/sqldelight/com/example/musicfy/db/Music.sq`. All timestamps remain
epoch millis. Key semantic differences preserved:

- `song.liked` and `song.inLibrary` are independent flags.
- `artist/album/playlist` likes are `bookmarkedAt`, not `liked`.
- `event` is the source of truth for history/stats; `playCount` is a legacy
  monthly aggregate.
- Import rollback uses `import_session_song` flags.
- Virtual playlists `LP_LIKED` / `LP_DOWNLOADED` are not real rows.

Room's `fallbackToDestructiveMigration` is **not** copied blindly: SQLDelight
requires explicit migrations, and `PRAGMA user_version = 41` is set on first
open so the transfer manifest stays compatible.

`SettingsKeys.kt` is generated from upstream `PreferenceKeys.kt` (322 keys),
and `SettingsStore` exposes typed accessors plus a typed snapshot compatible
with the `mochy-transfer` `settings/settings.json` format. Login tokens are
excluded from export, matching upstream `TransferArchive.LoginKeys`.

Java-serialized player state (`persistent_queue.data`,
`persistent_automix.data`, `persistent_player_state.data`) is not portable; it
must be replaced with `kotlinx.serialization` JSON in stage 3.

## Navigation and screens

`Screens.kt` is ported: four root tabs (`home`, `search_input`, `library`,
`settings`) plus enums `DarkMode`, `LyricsPosition`, `OptionStats`.

Android `NavigationBuilder.kt` declares ~70 routes. On iOS they are mapped to
a Compose Multiplatform `NavHost`; routes and argument names stay identical,
including `album/{albumId}`, `artist/{artistId}`, `search/{query}`, playlist,
library, settings, Audio Lab, equalizer and dev-space routes.

Key screen clusters and their upstream size:

| Cluster | Upstream volume | Notes for iOS |
|---|---:|---|
| Home + HeroCarousel | ~184 KB | Custom paging/parallax; heaviest UI cluster. |
| Player bottom sheet | ~104 KB | Mini/full sheet, gestures, lyrics/queue pages, style selector. |
| Lyrics glow + lyrics utils | ~250 KB | Per-word glow, auto-sync, translation, romanization. |
| Artist/Album/Playlist detail | ~230 KB | Shared-element transitions; `SharedTransitionLayout` is available in CMP. |
| Settings | ~200 KB | Many forms; import/sync and WebView flows need native bridges. |
| Stats/Recap | ~200 KB | Story UI and charts. |
| Search / Library | ~330 KB | Online/local search, suggestions, library tabs and import. |
| Audio Lab / EQ / DSP | ~170 KB | `AVAudioUnitEQ` replaces Media3 `AudioProcessor` chain. |
| Crossmix | ~117 KB | Beat-matched mix; one of the last items. |

## Player mapping

| Android/Media3 | iOS approach |
|---|---|
| `MusicService` foreground service + `MediaSession` | `iosMain` AVPlayer engine + `MPNowPlayingInfoCenter` / `MPRemoteCommandCenter`; background audio entitlement |
| uid-based queue, runway, radio, shuffle order | Shared Kotlin `PlayerQueue`; AVPlayer items fed from it |
| `CustomEqualizerAudioProcessor`, Biquad, limiter | `AVAudioUnitEQ`, `AVAudioUnitDynamicsProcessor`, custom DSP where needed |
| Reverb / spatial / loudness | `AVAudioUnitReverb` + custom DSP (parity requires custom nodes) |
| Crossfade (two ExoPlayers, sin/cos ramps) | Two AVPlayers with gain ramps in `AVAudioEngine` |
| Crossmix | Custom analysis + tempo scripts; deferred spike |
| `SimpleCache` / `DownloadManager` | `URLSession` file cache / `AVAssetDownloadTask` |
| Widevine / ClearKey | FairPlay + server license proxy; hard blocker for some sources |
| Cast (GMS) | AirPlay via `AVPlayer.allowsExternalPlayback` |
| Widgets, scrobble, haptics, spectrum | WidgetKit, Last.fm provider, CoreHaptics, vDSP FFT |

## Content providers

See `03-providers.md`. Summary: 13 providers (~9k non-empty Kotlin lines),
with `innertube` (~3.2k), Apple Music canvas, and DRM/licensing being the
riskiest. Lyrics providers and canvas matching are mostly pure HTTP/JSON and
can be ported directly with Ktor + `kotlinx.serialization`.

## Currently ported

- `MusicfyApp`, `Screens`, `Theme`, `Type` (Inter fonts bundled in composeResources).
- SQLDelight schema for the full 23-table core plus representative queries.
- `DatabaseDriverFactory` expect/actual with iOS `NativeSqliteDriver`.
- Generated 322-key `SettingsKeys` + `SettingsStore` snapshot.
- A set of self-contained helpers (ShapeUtils, UtilExt, LocalItem, enums, etc.).

Progress is tracked by `tools/verify.py` → `docs/04-port-status.md`.
