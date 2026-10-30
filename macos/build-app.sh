#!/usr/bin/env bash
set -euo pipefail

macos_root="$(cd "$(dirname "$0")" && pwd)"
project_root="$(cd "$macos_root/.." && pwd)"
build_root="${WEAVE_MAC_BUILD_ROOT:-$macos_root/.build}"
output_root="${WEAVE_MAC_OUTPUT_ROOT:-$macos_root/build}"
identity="${WEAVE_MAC_SIGN_IDENTITY:--}"
release="${WEAVE_MAC_RELEASE:-0}"
core_binary="$macos_root/Resources/mihomo"
source_commit="$(git -C "$project_root" rev-parse HEAD)"
source_dirty=false
if [[ -n "$(git -C "$project_root" status --porcelain --untracked-files=normal)" ]]; then source_dirty=true; fi

if [[ "$(uname -m)" != arm64 ]]; then
  echo 'This package currently requires an Apple Silicon build host and audited arm64 core.' >&2
  exit 1
fi
if [[ "$release" == 1 && ( "$identity" == - || -z "${WEAVE_MAC_NOTARY_PROFILE:-}" || "$source_dirty" == true ) ]]; then
  echo 'A release requires a clean commit, Developer ID identity and notarization keychain profile.' >&2
  exit 1
fi
if [[ ! -x "$core_binary" ]]; then
  echo "Required Mihomo executable is missing: $core_binary" >&2
  exit 1
fi
core_expected_sha="$(sed -n 's/^mihomo.darwin-arm64.sha256=//p' "$project_root/core-lock.properties")"
core_actual_sha="$(shasum -a 256 "$core_binary" | awk '{print $1}')"
if [[ -z "$core_expected_sha" || "$core_actual_sha" != "$core_expected_sha" ]]; then
  echo 'Mihomo SHA-256 does not match core-lock.properties' >&2
  exit 1
fi

export SDKROOT="${SDKROOT:-$(xcrun --sdk macosx --show-sdk-path)}"
swift build --package-path "$macos_root" --configuration release --scratch-path "$build_root" --jobs "${WEAVE_MAC_BUILD_JOBS:-4}"
bin_root="$(swift build --package-path "$macos_root" --configuration release --scratch-path "$build_root" --show-bin-path)"
mkdir -p "$output_root"
package_root="$(mktemp -d "$output_root/package.XXXXXX")"
app_bundle="$package_root/Weave.app"
mkdir -p "$app_bundle/Contents/MacOS" "$app_bundle/Contents/Resources"
cp "$macos_root/Resources/Info.plist" "$app_bundle/Contents/Info.plist"
cp "$bin_root/WeaveMac" "$app_bundle/Contents/MacOS/WeaveMac"
cp "$macos_root/Resources/Weave.icns" "$app_bundle/Contents/Resources/Weave.icns"
cp "$macos_root/Resources/WeaveImpressionTexture.webp" "$app_bundle/Contents/Resources/WeaveImpressionTexture.webp"
cp "$core_binary" "$app_bundle/Contents/Resources/mihomo"
cp "$project_root/LICENSE" "$project_root/NOTICE" "$project_root/THIRD_PARTY.md" "$project_root/core-lock.properties" "$app_bundle/Contents/Resources/"
cp -R "$macos_root/Resources/Licenses" "$app_bundle/Contents/Resources/Licenses"
cp "$macos_root/Package.resolved" "$app_bundle/Contents/Resources/Package.resolved"

version="$(/usr/libexec/PlistBuddy -c 'Print :CFBundleShortVersionString' "$app_bundle/Contents/Info.plist")"
python3 - "$app_bundle/Contents/Resources/build-info.json" "$source_commit" "$source_dirty" "$version" <<'PY'
import json,sys
from pathlib import Path
Path(sys.argv[1]).write_text(json.dumps({'commit':sys.argv[2],'dirty':sys.argv[3]=='true','version':sys.argv[4],'architecture':'arm64'},indent=2)+'\n')
PY

sign_flags=(--force --sign "$identity")
if [[ "$identity" != - ]]; then sign_flags+=(--options runtime --timestamp); fi
core_identifier='io.weave.client.macos.mihomo'
codesign "${sign_flags[@]}" --identifier "$core_identifier" "$app_bundle/Contents/Resources/mihomo"
core_signed_sha="$(shasum -a 256 "$app_bundle/Contents/Resources/mihomo" | awk '{print $1}')"
core_commit="$(sed -n 's/^mihomo.commit=//p' "$project_root/core-lock.properties")"
python3 - "$app_bundle/Contents/Resources/core-integrity.json" "$core_actual_sha" "$core_signed_sha" "$core_commit" "$core_identifier" <<'PY'
import json,sys
from pathlib import Path
Path(sys.argv[1]).write_text(json.dumps({'schema':1,'architecture':'arm64','coreIdentifier':sys.argv[5],
 'sourceCoreSHA256':sys.argv[2],'signedCoreSHA256':sys.argv[3],'coreCommit':sys.argv[4]},indent=2)+'\n')
PY
codesign "${sign_flags[@]}" --entitlements "$macos_root/WeaveMac.entitlements" "$app_bundle"
codesign --verify --deep --strict "$app_bundle"
codesign --verify --strict -R "=identifier \"$core_identifier\"" "$app_bundle/Contents/Resources/mihomo"

if [[ "$release" == 1 ]]; then
  ditto -c -k --sequesterRsrc --keepParent "$app_bundle" "$package_root/notarization-submission.zip"
  xcrun notarytool submit "$package_root/notarization-submission.zip" --keychain-profile "$WEAVE_MAC_NOTARY_PROFILE" --wait --output-format json > "$package_root/notarization.json"
  python3 - "$package_root/notarization.json" <<'PY'
import json,sys
if json.load(open(sys.argv[1]))['status'] != 'Accepted': raise SystemExit('Notarization was not accepted')
PY
  xcrun stapler staple "$app_bundle"
  xcrun stapler validate "$app_bundle"
fi

archive="$package_root/Weave-macos-arm64.zip"
ditto -c -k --sequesterRsrc --keepParent "$app_bundle" "$archive"
(cd "$package_root" && shasum -a 256 Weave-macos-arm64.zip > Weave-macos-arm64.zip.sha256)
python3 - "$package_root/manifest.json" "$source_commit" "$source_dirty" "$version" "$core_actual_sha" "$identity" "$release" "$core_signed_sha" <<'PY'
import json,sys
from pathlib import Path
Path(sys.argv[1]).write_text(json.dumps({'source_commit':sys.argv[2],'source_dirty':sys.argv[3]=='true','version':sys.argv[4],
 'architecture':'arm64','source_core_sha256':sys.argv[5],'signed_core_sha256':sys.argv[8],'signing':'ad-hoc-preview' if sys.argv[6]=='-' else 'identity-signed',
 'notarized':sys.argv[7]=='1','source_url':'https://github.com/sundaysebasidian-byte/weave-client/tree/'+sys.argv[2]},indent=2)+'\n')
PY
printf '%s\n' "$app_bundle" "$archive"
