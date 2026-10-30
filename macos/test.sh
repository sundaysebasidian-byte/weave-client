#!/bin/bash
set -euo pipefail
cd "$(dirname "$0")"
developer_dir="$(xcode-select -p)"
test_flags=(--disable-xctest)
# Apple's standalone CLT includes Testing but some releases omit its search paths.
if [[ "$developer_dir" == */CommandLineTools && -d "$developer_dir/Library/Developer/Frameworks/Testing.framework" ]]; then
  test_flags+=(
    -Xswiftc "-F$developer_dir/Library/Developer/Frameworks"
    -Xlinker "-F$developer_dir/Library/Developer/Frameworks"
    -Xlinker -rpath -Xlinker "$developer_dir/Library/Developer/Frameworks"
    -Xlinker -rpath -Xlinker "$developer_dir/Library/Developer/usr/lib")
fi
swift test "${test_flags[@]}" "$@"
