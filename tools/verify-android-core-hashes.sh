#!/usr/bin/env bash
set -euo pipefail

if command -v shasum >/dev/null 2>&1; then
    verify=(shasum -a 256 --check)
else
    verify=(sha256sum --check)
fi

for abi in arm64-v8a armeabi-v7a x86 x86_64; do
    # Assign separately: a failing reader must not become an empty comparison.
    if ! expected=$(awk -v key="libclash.$abi.sha256=" '
        index($0,key)==1 { count++; digest=substr($0,length(key)+1) }
        END { if (count != 1 || length(digest) != 64 || digest ~ /[^0-9a-f]/) exit 1; print digest }
    ' core-lock.properties); then
        echo "Missing, duplicate or invalid SHA-256 for $abi in core-lock.properties" >&2
        exit 1
    fi
    printf '%s  %s\n' "$expected" "app/src/main/jniLibs/$abi/libclash.so" | "${verify[@]}"
done
