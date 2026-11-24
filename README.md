# Musicfy iOS port (Kotlin Multiplatform + Compose Multiplatform)

This repository ports Musicfy 8.1.2 to iOS **without rewriting the UI in Swift**.
All screens, ViewModels and business logic are Kotlin/Compose shared code.
`iosApp/` contains only the mandatory Xcode bootstrap that presents the
Compose UIViewController.

Status: early scaffolding. See `docs/04-port-status.md` for progress.

## Layout

```
composeApp/src/commonMain/kotlin/   # ported Kotlin/Compose (compiled for iOS)
composeApp/src/commonMain/sqldelight/ # Room v41 schema equivalent
composeApp/src/iosMain/kotlin/      # iOS drivers and platform bridges
iosApp/                             # Xcode project + Swift bootstrap
reference/                          # original Android sources (not compiled)
docs/                               # architecture, roadmap, provider map, status
tools/verify.py                     # static checker (runs on Windows)
```

## Local static check

```powershell
python tools/verify.py
```

Writes `docs/04-port-status.md` with the ported-file count and warnings.

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

GPL-3.0, inherited from upstream Musicfy. See `LICENSE`.
