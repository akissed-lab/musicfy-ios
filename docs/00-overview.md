# Musicfy → iOS: overview

## Goal

Full port of Musicfy 8.1.2 UI and behaviour to iOS **without rewriting the UI
in Swift**. The port is Kotlin Multiplatform + Compose Multiplatform: all
screens, components, ViewModels and business logic stay Kotlin/Compose.

## Source facts (verified 2026-11-24)

- Release tag `musicfy-8.1.2` (`b73b4a3`) contains only a farewell README; the
  release assets are two APKs and no source code.
- Actual Android sources live in branch `main` (HEAD `db2b3d1`): 837 Kotlin
  files, ~186 000 lines including providers and modules; 113 000 lines are
  Compose UI under `app/src/main/kotlin/.../ui`.
- The unmodified upstream sources are kept in `reference/` for side-by-side
  porting; they are **not compiled**.
- GPL-3.0 upstream; the derived work stays GPL-3.0 (see root `LICENSE`).

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
- Local static check: `python tools/verify.py` (writes `docs/04-port-status.md`).

## Repository layout

```
composeApp/src/commonMain/kotlin/  # ported Kotlin (compiled for iOS)
composeApp/src/iosMain/kotlin/     # iOS entry point + Darwin implementations
reference/                         # original Android sources (not compiled)
iosApp/                            # Xcode project and Swift bootstrap
docs/                              # architecture, roadmap, provider map, status
tools/verify.py                    # static checker
```
