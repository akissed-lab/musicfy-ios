# Porting roadmap

Status legend: `[ ]` not started, `[~]` in progress, `[x]` done for the stage
(base scaffolding only; a macOS build is still required to validate).

## Current focus — next actions

1. Finish the core KMP entity/relation models (`Song`, `Album`, `Artist`,
   `Playlist`, map tables, events, import tables) and align SQLDelight defaults
   with Room (`romanizeLyrics = 1`, `date` as an integer timestamp).
2. Get `:composeApp:compileKotlinIosSimulatorArm64` green on Windows. The
   Kotlin 2.3.10 compiler cannot consume KLIBs built by 2.4.x, so
   `navigation-compose` is pinned to `2.10.0-beta01`, `lifecycle-viewmodel-compose`
   to `2.10.0` and `kotlinx-serialization-json` to `1.10.0`.
   `tools/check_klib_abi.py` verifies these pins.
3. Port `YTItem` models plus `SpeedDialItem` conversions, then the
   `MusicDatabase` facade and the ~25 most-used `DatabaseDao` operations on
   SQLDelight.
4. Port `NavigationBuilder` + `AppNavigation`: all ~70 upstream routes must
   exist in Compose before individual screens are filled in.
5. Port shared UI components and the four core tabs (Home/Search/Library/
   Settings), then the player overlay, mini player, queue and lyrics screen.
6. Apply the **Apple Music-style liquid glass** design system to every screen:
   all screens use `GlassRoot`, `glassSurface`, `GlassCard`, `GlassNavigationBar`
   and the shared aurora backdrop. No screen may introduce its own opaque
   chrome that breaks the glass layer.

A real iOS build, signing and `.ipa` still require macOS/Xcode.

## Stage 0 — Recon and project setup

- [x] Verify release tag contents and locate actual sources (`main`).
- [x] Analyse architecture, data, UI/player and providers.
- [x] Copy GPL-3.0 license and reference sources.
- [x] Set up KMP/CMP Gradle project + Xcode host (Swift only as bootstrap).
- [x] Add static checker and progress report.
- [x] Pin Gradle/CMP/KLIB dependency versions compatible with Kotlin 2.3.10.
- [ ] Validate the pinned versions on macOS and commit generated `Res` usage.

## Stage 1 — Domain + storage

- [x] SQLDelight schema: 23 Room tables + 3 views at schema version 41.
- [x] iOS SQLite driver with WAL/busy_timeout/foreign_keys pragmas.
- [x] 322 settings keys generated from upstream; typed `SettingsStore`.
- [~] Port domain models (`MediaMetadata`, `LyricsEntity`); remaining
      `Song`/`Album`/`Artist` relations still pending.
- [ ] Port `MusicDatabase` facade operations and `DatabaseDao` helpers.
- [ ] Port transfer ARCHIVE export/import (manifest v1, typed settings JSON).
- [x] Replace Java-serialized player state with `Clock.System.now()`;
      full kotlinx.serialization migration still pending for other models.

## Stage 2 — Provider layer

- [x] Shared HTTP/JSON infrastructure (Darwin engine, `createPlatformHttpClient`).
- [x] Lyrics providers: lrclib, kugou, betterlyrics, simpmusic, youlyplus,
      directlyrics (Deezer/NetEase/QQ), paxsenix.
- [x] Innertube lyrics slice: transcript, captions→LRC, lyrics browse tab;
      12/12 sources registered in `LyricsProviderRegistry`.
- [x] `LyricsHelper`: provider priorities, caching, offline handling, embedded
      tag priority and source grading.
- [~] Canvas graph (`canvas`, `applecanvas`, `artistvideo`) still pending.
- [ ] Last.fm scrobbling (`api_sig` must move behind a proxy).
- [ ] ShazamKit replacement for `providers/shazamkit`.
- [~] Full Innertube API and volatile keys/po-tokens behind a server proxy.
- [ ] Apple Music module with MusicKit + FairPlay server proxy.

## Stage 3 — Playback

- [ ] AVPlayer engine, uid queue, gapless/crossfade, background session.
- [ ] Now Playing / remote commands / AirPlay / sleep timer.
- [ ] Cache and downloads; source-module fallback and retry matrix.
- [ ] EQ / Audio Lab / spectrum / haptics on AVAudioEngine.
- [x] Lyrics parse/sync core, tag reading, auto-offset alignment, vocal
      activity analysis and translation orchestration ported (`LyricsAligner`,
      `LyricsAutoSync`, `VocalActivity`, `LyricsTranslationHelper`,
      `GoogleTranslateEngine`). The iOS PCM decoder bridge is still pending;
      romanisation tables are a later stage.

## Stage 4 — UI (full Compose port)

- [x] App root, 4-tab navigation skeleton, dark-only theme and Inter typography.
- [~] Apple Music-style liquid glass design system (`GlassRoot`, `glassSurface`,
      `GlassCard`, `GlassNavigationBar`, aurora backdrop) is wired into the app
      root; every ported screen must consume it instead of opaque Material chrome.
- [ ] NavigationBuilder: all ~70 routes and transitions.
- [ ] Player overlay: bottom sheet, mini player, styles, gestures, queue, lyrics.
- [ ] Home/Search/Library/Settings screens.
- [ ] Detail screens: album, artist, playlists, browse, genre.
- [ ] Stats/Recap, onboarding, profile, update/Mochy bridge sheets.
- [ ] Shared component library (`ui/component`, ~600 KB upstream).

## Stage 5 — Integrations and release

- [~] Local files: ID3/FLAC/M4A lyric tag reading ported (`EmbeddedLyrics`);
      iOS document picker still pending.
- [ ] Account import/sync flows and external login bridges.
- [ ] WidgetKit widgets and App Group storage.
- [ ] macOS CI job (build + tests on simulator), TestFlight instructions.

## Notes

- Compose Multiplatform 1.12.x no longer publishes an `iosX64` artifact, so
  the port targets `iosArm64` and `iosSimulatorArm64` (device + Apple Silicon
  simulator). The Xcode project never declared an x86_64 simulator slice.
- Coil 3.3.0 is kept to match upstream; its Skiko constraint differs from the
  one used by Compose 1.12.2. Gradle selects the newer Compose version and the
  compatibility checker reports a warning. Revisit when Coil publishes a
  version built against Kotlin 2.3.x.
- All 1,206 upstream string/plural resources are copied verbatim into
  `composeResources/values` via `tools/copy_strings.py`.

This is a multi-stage port of ~186k lines. Static checks on Windows do not
replace compilation; each stage is only truly complete after a successful
build on macOS.
