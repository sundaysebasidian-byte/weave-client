#!/usr/bin/env bash
set -euo pipefail
brand_root="$(cd "$(dirname "$0")/.." && pwd)"
brand_source="${1:-$brand_root/docs/images/weave-icon-master.png}"
brand_work_dir="$(mktemp -d "${TMPDIR:-/tmp}/weave-brand-export.XXXXXX")"
trap 'rm -rf "$brand_work_dir"' EXIT
swiftc -module-cache-path "$brand_work_dir/swift-cache" "$brand_root/macos/Tools/ExportBrandIcon.swift" -o "$brand_work_dir/export"
"$brand_work_dir/export" "$brand_source" "$brand_work_dir/master.png" alpha
cp "$brand_work_dir/master.png" "$brand_root/macos/Resources/WeaveIcon-1024.png"
sips -z 512 512 "$brand_work_dir/master.png" --out "$brand_work_dir/icon-512.png" >/dev/null
cwebp -quiet -lossless "$brand_work_dir/icon-512.png" -o "$brand_root/docs/images/weave-icon.webp"
cp "$brand_root/docs/images/weave-icon.webp" "$brand_root/app/src/main/res/drawable-nodpi/ic_launcher_art.webp"
sips -z 256 256 "$brand_work_dir/master.png" --out "$brand_root/windows/src/Weave.Windows/Assets/Weave.png" >/dev/null
"$brand_work_dir/export" "$brand_source" "$brand_root/ios/WeaveIOS/Resources/Assets.xcassets/AppIcon.appiconset/WeaveIcon-1024.png" opaque
for brand_size in 16 32 128 256 512; do
    sips -z "$brand_size" "$brand_size" "$brand_work_dir/master.png" --out "$brand_root/macos/Resources/Weave.iconset/icon_${brand_size}x${brand_size}.png" >/dev/null
    brand_double=$((brand_size * 2))
    sips -z "$brand_double" "$brand_double" "$brand_work_dir/master.png" --out "$brand_root/macos/Resources/Weave.iconset/icon_${brand_size}x${brand_size}@2x.png" >/dev/null
done
swiftc -module-cache-path "$brand_work_dir/swift-cache" "$brand_root/macos/Tools/BuildIcns.swift" -o "$brand_work_dir/icns"
"$brand_work_dir/icns" "$brand_root/macos/Resources/Weave.iconset" "$brand_root/macos/Resources/Weave.icns"
echo 'Exported Android, README, macOS, Windows and opaque iOS icons from the repaired master.'
