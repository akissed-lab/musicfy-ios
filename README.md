# Mochy Music iOS port (Kotlin Multiplatform + Compose Multiplatform)

This repository ports **Mochy Music 8.5.0.0 RC1** (upstream release
`mochy-8.5.0.0`, branch `main` at `e6a17d2eb9f48b503fe07e35049cb522b8486db2`)
to iOS **without rewriting the UI in Swift**. All screens, ViewModels and
business logic are Kotlin/Compose shared code. `iosApp/` contains only the
mandatory Xcode bootstrap that presents the Compose UIViewController.

The upstream project was renamed from `realidkroo/musicfy` to
[`realidkroo/Mochy-Music`](https://github.com/realidkroo/Mochy-Music). The old
`db2b3d1` (8.1.2) tree is a historical baseline, not the source of truth; the
rename carries 646 commits and roughly 300 changed files. Full provenance and
the `reference/` policy are in `docs/10-upstream-policy.md`.

Status: see `docs/04-port-status.md` for progress and `docs/09-work-packages.md`
for landed waves and the wave 9 scope.

## Layout

```
composeApp/src/commonMain/kotlin/   # ported Kotlin/Compose (compiled for iOS)
composeApp/src/commonMain/sqldelight/ # Room v41 schema equivalent
composeApp/src/iosMain/kotlin/      # iOS drivers and platform bridges
iosApp/                             # Xcode project + Swift bootstrap
reference/                          # local upstream snapshot (not compiled); legacy db2b3d1 until refreshed
docs/                               # architecture, roadmap, provider map, status
tools/verify.py                     # static checker (runs on Windows)
```

## Local static check

```powershell
python tools/verify.py
```

Writes `docs/04-port-status.md` with the ported-file count and warnings. The
check is pure Python: no Gradle, Xcode or network. It counts whatever is in
`reference/`, which is still the legacy `db2b3d1` snapshot; refresh that tree
from Mochy Music `main` (`e6a17d2…`) before treating the percentage as current.

## Building (requires macOS + Xcode)

1. Open `iosApp/iosApp.xcodeproj` in Xcode.
2. Set `iosApp/Configuration/Config.xcconfig`:
   - `TEAM_ID` — your Apple Developer team ID (leave empty for simulator).
   - `BUNDLE_ID` — bundle identifier.
   - `APP_NAME` — display name.
3. Select an iOS simulator and run. Xcode executes
   `./gradlew :composeApp:embedAndSignAppleFrameworkForXcode` automatically.

The Gradle build downloads dependencies from Maven Central/JitPack. An Apple
Developer account is required only for device installation and TestFlight.

## CI / IPA build

GitHub Actions builds the iOS artifact on a macOS runner. Push to `main` or
`master`, or run **Build iOS IPA** manually in the Actions tab. The workflow
compiles the Kotlin framework with Gradle, archives the Xcode project and
uploads an IPA artifact. Add `SIGNING_CERT_P12`, `SIGNING_CERT_PASSWORD` and
`SIGNING_PROFILE_BASE64` secrets to get a signed IPA; without them the artifact
is unsigned. Details: `docs/07-github-actions.md`.

## License

GPL-3.0, inherited from upstream Mochy Music (formerly Musicfy). See `LICENSE`.
