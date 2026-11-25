# Mochy Music → iOS: overview

## Goal

Full port of Mochy Music 8.5.0.0 RC1 UI and behaviour to iOS **without
rewriting the UI in Swift**. The port is Kotlin Multiplatform + Compose
Multiplatform: all screens, components, ViewModels and business logic stay
Kotlin/Compose.

## Source of truth (verified 2026-11-25)

- Upstream: https://github.com/realidkroo/Mochy-Music , branch `main`, HEAD
  `e6a17d2eb9f48b503fe07e35049cb522b8486db2`, release `mochy-8.5.0.0` RC1.
- Historical baseline: `realidkroo/musicfy` `db2b3d1` (8.1.2 source). The old
  `musicfy-8.1.2` tag (`b73b4a3`) held only a farewell README and two APKs; the
  actual sources were on `main`.
- The project was renamed from Musicfy to Mochy Music, completing the "Move to
  Mochy" transition that already existed in the legacy Android tree. The
  rename spans **646 commits / ~300 changed files**, so old file and line
  counts describe history, not the target.
- The local `reference/` tree is still the legacy `db2b3d1` snapshot (837
  Kotlin files, ~186 000 lines; ~113 000 lines of Compose UI under
  `app/src/main/kotlin/.../ui`). Treat those figures as baseline-only until
  the snapshot is refreshed from the new upstream.
- GPL-3.0 upstream; the derived work stays GPL-3.0 (see root `LICENSE`).

## Reference policy

`reference/` is a **local snapshot for side-by-side porting, not compiled and
not fetched at verification time**. `tools/verify.py` counts Kotlin files in
that tree and in `composeApp/src`; its percentage is therefore relative to
whatever snapshot is checked out. The source of truth is the Mochy Music
upstream above. To rebaseline, refresh `reference/` from `main` at `e6a17d2…`,
regenerate the derived settings/string artifacts, rerun `python tools/verify.py`
(no Gradle) and update the counts. Details: `docs/10-upstream-policy.md`.

## Chosen architecture

| Android | iOS port |
|---|---|
| Jetpack Compose 1.10.2 | Compose Multiplatform 1.12.2 |
| Kotlin 2.3.10 | Kotlin 2.3.10 (same language/plugin) |
| Gradle multi-module | Single `:composeApp` KMP module + `iosApp` Xcode project |
| Hilt, Room, DataStore, Media3, Coil, WorkManager | To be replaced with KMP-compatible equivalents (see `01-architecture.md`) |
| Kotlin coroutines / serialization / Ktor | Same libraries, Darwin Ktor engine |

The Xcode project contains only the mandatory ~10-line Swift bootstrap that
presents the Compose UIViewController. No application UI is written in Swift.

## Environment constraints

- Development host is Windows: no Xcode, no iOS SDK, no Apple signing. Sources
  are written and checked statically; the real build must run on macOS.
- Local static check: `python tools/verify.py` (writes `docs/04-port-status.md`);
  no Gradle or network access, snapshot-relative counts.

## Repository layout

```
composeApp/src/commonMain/kotlin/  # ported Kotlin (compiled for iOS)
composeApp/src/iosMain/kotlin/     # iOS entry point + Darwin implementations
reference/                         # local upstream snapshot (not compiled; legacy db2b3d1 for now)
iosApp/                            # Xcode project and Swift bootstrap
docs/                              # architecture, roadmap, provider map, status
tools/verify.py                    # static checker
```
