#!/usr/bin/env bash
set -euo pipefail

macos_root="$(cd "$(dirname "$0")" && pwd)"
project_root="$(cd "$macos_root/.." && pwd)"
build_root="${WEAVE_MAC_BUILD_ROOT:-/private/tmp/weave-macos-release}"
sdk_root="${SDKROOT:-/Library/Developer/CommandLineTools/SDKs/MacOSX15.4.sdk}"
output_root="$project_root/macos/build"
app_bundle="$output_root/Weave.app"
archive="$output_root/Weave-macos-arm64.zip"
core_binary="$macos_root/Resources/mihomo"
icon_source="$macos_root/Resources/WeaveIcon-1024.png"
core_expected_sha="$(sed -n 's/^mihomo.darwin-arm64.sha256=//p' "$project_root/core-lock.properties")"

env \
  SDKROOT="$sdk_root" \
  CLANG_MODULE_CACHE_PATH="${CLANG_MODULE_CACHE_PATH:-/private/tmp/weave-swift-module-cache}" \
  SWIFTPM_CUSTOM_LIBCACHE="${SWIFTPM_CUSTOM_LIBCACHE:-/private/tmp/weave-swift-libcache}" \
  swift build \
    --package-path "$macos_root" \
    --disable-sandbox \
    --configuration release \
    --scratch-path "$build_root"

rm -rf "$app_bundle"
mkdir -p "$app_bundle/Contents/MacOS" "$app_bundle/Contents/Resources"
cp "$macos_root/Resources/Info.plist" "$app_bundle/Contents/Info.plist"
cp "$build_root/arm64-apple-macosx/release/WeaveMac" "$app_bundle/Contents/MacOS/WeaveMac"
if [[ -f "$icon_source" ]]; then
  iconset_root="${TMPDIR:-/private/tmp}/weave-iconset"
  rm -rf "$iconset_root"
  mkdir -p "$iconset_root"
  for size in 16 32 128 256 512; do
    sips -z "$size" "$size" "$icon_source" --out "$iconset_root/icon_${size}x${size}.png" >/dev/null
    double_size=$((size * 2))
    sips -z "$double_size" "$double_size" "$icon_source" --out "$iconset_root/icon_${size}x${size}@2x.png" >/dev/null
  done
  env \
    SDKROOT="$sdk_root" \
    CLANG_MODULE_CACHE_PATH="${CLANG_MODULE_CACHE_PATH:-/private/tmp/weave-swift-module-cache}" \
    SWIFTPM_CUSTOM_LIBCACHE="${SWIFTPM_CUSTOM_LIBCACHE:-/private/tmp/weave-swift-libcache}" \
    swiftc "$macos_root/Tools/BuildIcns.swift" -o "${TMPDIR:-/private/tmp}/weave-build-icns"
  "${TMPDIR:-/private/tmp}/weave-build-icns" "$iconset_root" "$app_bundle/Contents/Resources/Weave.icns"
fi
if [[ -f "$macos_root/Resources/WeaveImpressionTexture.webp" ]]; then
  cp "$macos_root/Resources/WeaveImpressionTexture.webp" "$app_bundle/Contents/Resources/WeaveImpressionTexture.webp"
fi
if [[ -x "$core_binary" ]]; then
  core_actual_sha="$(shasum -a 256 "$core_binary" | awk '{print $1}')"
  if [[ -z "$core_expected_sha" || "$core_actual_sha" != "$core_expected_sha" ]]; then
    echo "Mihomo SHA-256 does not match core-lock.properties" >&2
    exit 1
  fi
  cp "$core_binary" "$app_bundle/Contents/Resources/mihomo"
fi

codesign \
  --force \
  --sign - \
  --entitlements "$macos_root/WeaveMac.entitlements" \
  "$app_bundle"

rm -f "$archive"
ditto -c -k --sequesterRsrc --keepParent "$app_bundle" "$archive"
shasum -a 256 "$archive" > "$archive.sha256"

echo "$app_bundle"
echo "$archive"
