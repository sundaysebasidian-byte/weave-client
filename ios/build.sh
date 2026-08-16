#!/bin/zsh
set -euo pipefail

repo_root="${0:A:h:h}"
xcode_developer="${DEVELOPER_DIR:-/Applications/Xcode.app/Contents/Developer}"

if [[ ! -x "$xcode_developer/usr/bin/xcodebuild" ]]; then
    print -u2 "完整 Xcode 未安装；请安装 Xcode 16+ 或设置 DEVELOPER_DIR。"
    exit 2
fi

export DEVELOPER_DIR="$xcode_developer"
WEAVE_MIHOMO_BINARY="$repo_root/macos/Resources/mihomo" \
    swift run --package-path "$repo_root/ios/WeaveCore" WeaveCoreSelfTest
xcodebuild \
    -project "$repo_root/ios/WeaveIOS.xcodeproj" \
    -scheme WeaveIOS \
    -configuration Debug \
    -destination 'generic/platform=iOS Simulator' \
    -derivedDataPath "$repo_root/ios/.deriveddata" \
    CODE_SIGNING_ALLOWED=NO \
    build
