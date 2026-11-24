#!/usr/bin/env bash
# Imports a base64-encoded P12 + mobileprovision into a temporary keychain and
# writes an exportOptions.plist for xcodebuild -exportArchive. If signing
# secrets are absent, the workflow falls back to an unsigned IPA artifact.
set -euo pipefail

echo "ready=false" >> "$GITHUB_OUTPUT"

if [[ -z "${SIGNING_CERT_P12:-}" || -z "${SIGNING_CERT_PASSWORD:-}" || -z "${SIGNING_PROFILE_BASE64:-}" ]]; then
  echo "Signing secrets are not configured; the workflow will produce an unsigned IPA."
  exit 0
fi

WORK_DIR="$RUNNER_TEMP/musicfy-signing"
KEYCHAIN="$WORK_DIR/musicfy.keychain-db"
KEYCHAIN_PASSWORD="$(openssl rand -base64 32 | tr -d '\n')"
mkdir -p "$WORK_DIR" "$GITHUB_WORKSPACE/build"

printf '%s' "$SIGNING_CERT_P12" | base64 --decode > "$WORK_DIR/certificate.p12"
printf '%s' "$SIGNING_PROFILE_BASE64" | base64 --decode > "$WORK_DIR/profile.mobileprovision"

security create-keychain -p "$KEYCHAIN_PASSWORD" "$KEYCHAIN"
security set-keychain-settings -lut 21600 "$KEYCHAIN"
security unlock-keychain -p "$KEYCHAIN_PASSWORD" "$KEYCHAIN"
security import "$WORK_DIR/certificate.p12" \
  -k "$KEYCHAIN" \
  -P "$SIGNING_CERT_PASSWORD" \
  -T /usr/bin/codesign \
  -T /usr/bin/xcodebuild \
  -T /usr/bin/security
security set-key-partition-list -S apple-tool:,apple:,codesign: -s -k "$KEYCHAIN_PASSWORD" "$KEYCHAIN"

KEYCHAINS="$(security list-keychains -d user | tr -d '\"' | tr '\n' ' ')"
# shellcheck disable=SC2086
security list-keychains -d user -s "$KEYCHAIN" $KEYCHAINS

security cms -D -i "$WORK_DIR/profile.mobileprovision" > "$WORK_DIR/profile.plist"
PROFILE_UUID="$(/usr/libexec/PlistBuddy -c 'Print :UUID' "$WORK_DIR/profile.plist")"
PROFILE_NAME="$(/usr/libexec/PlistBuddy -c 'Print :Name' "$WORK_DIR/profile.plist")"
PROFILE_TEAM="$(/usr/libexec/PlistBuddy -c 'Print :TeamIdentifier:0' "$WORK_DIR/profile.plist" || true)"
TEAM_ID="${SIGNING_TEAM_ID:-$PROFILE_TEAM}"
BUNDLE_ID="${BUNDLE_ID:-com.example.musicfy.ios}"
METHOD="${EXPORT_METHOD:-ad-hoc}"

mkdir -p "$HOME/Library/MobileDevice/Provisioning Profiles"
cp "$WORK_DIR/profile.mobileprovision" \
  "$HOME/Library/MobileDevice/Provisioning Profiles/$PROFILE_UUID.mobileprovision"

cat > "$GITHUB_WORKSPACE/build/exportOptions.plist" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<!DOCTYPE plist PUBLIC "-//Apple//DTD PLIST 1.0//EN" "http://www.apple.com/DTDs/PropertyList-1.0.dtd">
<plist version="1.0">
<dict>
  <key>destination</key>
  <string>export</string>
  <key>method</key>
  <string>${METHOD}</string>
  <key>signingStyle</key>
  <string>manual</string>
  <key>teamID</key>
  <string>${TEAM_ID}</string>
  <key>provisioningProfiles</key>
  <dict>
    <key>${BUNDLE_ID}</key>
    <string>${PROFILE_NAME}</string>
  </dict>
</dict>
</plist>
EOF
plutil -lint "$GITHUB_WORKSPACE/build/exportOptions.plist"

echo "ready=true" >> "$GITHUB_OUTPUT"
echo "profile_name=$PROFILE_NAME" >> "$GITHUB_OUTPUT"
echo "team_id=$TEAM_ID" >> "$GITHUB_OUTPUT"
echo "Configured signing profile: $PROFILE_NAME ($TEAM_ID), method: $METHOD"
