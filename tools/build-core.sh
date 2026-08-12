#!/usr/bin/env bash
set -euo pipefail

if [[ $# -lt 2 || $# -gt 4 ]]; then
  echo "usage: $0 <cmfa-checkout> <ndk-directory> [output-directory] [--print-hashes]" >&2
  exit 2
fi

project_root="$(cd "$(dirname "$0")/.." && pwd)"
lock_file="$project_root/core-lock.properties"
cmfa_root="$(cd "$1" && pwd)"
ndk_root="$(cd "$2" && pwd)"
output_root="${3:-$project_root/app/src/main/jniLibs}"
hash_mode="${4:-verify}"

if [[ "$hash_mode" != "verify" && "$hash_mode" != "--print-hashes" ]]; then
  echo "unknown hash mode: $hash_mode" >&2
  exit 2
fi

read_property() {
  awk -F= -v key="$1" '$1 == key { sub(/^[^=]*=/, ""); print; exit }' "$lock_file"
}

require_equal() {
  local label="$1"
  local actual="$2"
  local expected="$3"
  if [[ "$actual" != "$expected" ]]; then
    echo "$label mismatch: expected $expected, got $actual" >&2
    exit 1
  fi
}

require_equal \
  "CMFA commit" \
  "$(git -C "$cmfa_root" rev-parse HEAD)" \
  "$(read_property cmfa.commit)"
require_equal \
  "Mihomo commit" \
  "$(git -C "$cmfa_root/core/src/foss/golang/clash" rev-parse HEAD)" \
  "$(read_property mihomo.commit)"

patch_path="$project_root/$(read_property cmfa.patch)"
if [[ ! -f "$patch_path" ]]; then
  echo "required CMFA patch missing: $patch_path" >&2
  exit 1
fi
require_equal \
  "CMFA patch" \
  "$(shasum -a 256 "$patch_path" | awk '{print $1}')" \
  "$(read_property cmfa.patch.sha256)"
if ! git -C "$cmfa_root" apply --check --reverse "$patch_path"; then
  echo "required CMFA patch is not applied to checkout" >&2
  exit 1
fi

require_equal \
  "Go version" \
  "$(go env GOVERSION)" \
  "go$(read_property go.version)"
require_equal \
  "NDK version" \
  "$(awk -F' = ' '$1 == "Pkg.Revision" { print $2; exit }' "$ndk_root/source.properties")" \
  "$(read_property ndk.version)"

module_root="$cmfa_root/core/src/foss/golang"
toolchain="$ndk_root/toolchains/llvm/prebuilt"
if [[ "$(uname -s)" == "Darwin" ]]; then
  llvm_bin="$toolchain/darwin-x86_64/bin"
else
  llvm_bin="$toolchain/linux-x86_64/bin"
fi

build_one() {
  local abi="$1"
  local cc_prefix="$2"
  local goarch="$3"
  local goarm="$4"
  local destination="$output_root/$abi/libclash.so"

  mkdir -p "$(dirname "$destination")"
  (
    cd "$module_root"
    env \
      CC="$llvm_bin/${cc_prefix}21-clang" \
      GOOS=android \
      GOARCH="$goarch" \
      GOARM="$goarm" \
      CGO_ENABLED=1 \
      CFLAGS="-O3 -Werror" \
      GOPROXY="${GOPROXY:-off}" \
      go build \
        -buildmode c-shared \
        -trimpath \
        -o "$destination" \
        -tags "$(read_property build.tags)" \
        -ldflags "-s -w" \
        cfa/native
  )

  local expected
  expected="$(read_property "libclash.$abi.sha256")"
  local actual
  if command -v sha256sum >/dev/null 2>&1; then
    actual="$(sha256sum "$destination" | awk '{print $1}')"
  else
    actual="$(shasum -a 256 "$destination" | awk '{print $1}')"
  fi
  if [[ "$hash_mode" == "--print-hashes" ]]; then
    echo "libclash.$abi.sha256=$actual"
  else
    require_equal "$abi libclash.so" "$actual" "$expected"
    echo "$abi verified: $actual"
  fi
}

build_one arm64-v8a aarch64-linux-android arm64 ""
build_one armeabi-v7a armv7a-linux-androideabi arm 7
build_one x86 i686-linux-android 386 ""
build_one x86_64 x86_64-linux-android amd64 ""
