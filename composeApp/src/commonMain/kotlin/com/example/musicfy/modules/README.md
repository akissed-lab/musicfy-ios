# Modules on iOS (PR #407 safe surface)

This package ports only the portable, data-driven part of upstream Mochy 8.5's
module system. A module is an in-memory registration of data providers and
Compose content; the host owns all track, queue and playback state.

## API

- `ModuleDescriptor` — id (validated to a safe key), name, version, capabilities.
- `ModuleCapability` — `Lyrics`, `Queue`, `SongInfo`, `Sheets`.
- `ModuleLyricsProvider` / `ModuleLyrics` / `ModuleLyricLine` / `ModuleLyricWord`
  — timed lines with optional word-by-word stamps, parsed from host-supplied LRC.
- `ModuleQueueProvider` — projects a host-owned `ModuleQueueInput` snapshot into
  a read-only `ModuleQueueView`; it cannot change the actual queue.
- `ModuleSongInfoContributor` — adds facts derived from host metadata.
- `ModuleSheet` — a small over-player sheet with a composable body and a height
  fraction; shown by `ui.screens.modules.ModuleSheetHost`.
- `ModuleRegistry` — built-in safe modules plus `register*`, `unregister`,
  `setEnabled`, `findSheet` and provider lookup APIs.

`ModuleLrcParser` is a capped pure-Kotlin parser for normal LRC and enhanced
`<mm:ss.xx>` word stamps. No regex result is passed to a native API.

## Intentionally unsupported on iOS

These upstream features are deliberately **not** ported:

- JavaScript / EEL / liveprog script engines and any arbitrary code loading
  (`DexClassLoader`, `PathClassLoader`, signed APKs, native `.so` libraries).
- Zip/APK scanning, manifest extraction, unpacking module assets or fonts.
- Private-network / URL guards, sandboxed HTTP clients, proxy and DNS hooks,
  sign-in web views and session-header capture.
- Secret storage, secret-vault sealing, secret probing/redaction, key rotation.
- File sandbox escapes, canonical-path checks, module data folders and delete
  trees. Modules here never receive a `File`, `Uri`, `Context` or socket.

The iOS registry is therefore safe by construction: enabling a module can only
change which data and composables the app chooses to display.

## Suggested integration hook

Call `registerExampleModuleSheets()` once during app startup (next to the other
service setup), place `ModuleSheetHost()` alongside the app's other overlays
inside `GlassRoot`, and call `ModuleSheetRequests.open(moduleId, sheetId)` from
a host-owned button. `ModuleLyricsPage`, `ModuleQueuePage` and
`ModuleSongInfoPage` can be hosted from the player/lyrics/queue screens by
passing the host's existing `ModuleTrackQuery`, `QueueState` and position flow.
