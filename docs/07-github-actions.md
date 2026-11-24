# GitHub Actions: IPA build

`/.github/workflows/ios-ipa.yml` builds the real iOS artifact on a macOS runner:

1. checks out the repository and installs JDK 21;
2. compiles the Kotlin/Native `ComposeApp` framework for `iosArm64`;
3. runs `xcodebuild archive` for the shared `iosApp` scheme;
4. exports a signed IPA (when secrets are configured) or packages an unsigned
   IPA from the archived `.app`;
5. uploads the IPA as a workflow artifact named `musicfy-ipa-signed` or
   `musicfy-ipa-unsigned`.

The workflow runs on pushes to `main`/`master` and can be started manually from
the Actions tab. Manual runs accept `export_method` (`ad-hoc` or `app-store`).

## Signed IPA

Add these repository secrets (Settings → Secrets and variables → Actions):

| Secret | Meaning |
| --- | --- |
| `SIGNING_CERT_P12` | Base64 of a `.p12` certificate (`base64 -i cert.p12 \| tr -d '\n'`) |
| `SIGNING_CERT_PASSWORD` | Password for the `.p12` |
| `SIGNING_PROFILE_BASE64` | Base64 of the `.mobileprovision` file |
| `SIGNING_TEAM_ID` | Optional; inferred from the profile when omitted |

The provisioning profile bundle ID must match `com.example.musicfy.ios`
(or update `iosApp/Configuration/Config.xcconfig` and the workflow together).
For TestFlight/App Store use `export_method=app-store` and an App Store profile
plus an Apple Distribution certificate. For ad-hoc use an ad-hoc profile.

The signing script creates a temporary keychain on the runner and deletes it
when the job ends. Secrets are never written to the repository.

## Unsigned fallback

Without signing secrets the workflow still produces `Musicfy-unsigned.ipa`.
It is useful for build verification, but iOS will refuse to install it unless
it is re-signed on a Mac with a valid certificate and profile.

## Local equivalent

```bash
./gradlew :composeApp:linkReleaseFrameworkIosArm64
xcodebuild -project iosApp/iosApp.xcodeproj -scheme iosApp \
  -configuration Release -destination 'generic/platform=iOS' \
  -archivePath build/iosApp.xcarchive archive
```
