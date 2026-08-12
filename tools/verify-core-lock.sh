#!/usr/bin/env bash
set -euo pipefail

lock_file="${1:-core-lock.properties}"

read_property() {
  awk -F= -v key="$1" '$1 == key { sub(/^[^=]*=/, ""); print; exit }' "$lock_file"
}

verify_commit() {
  local label="$1"
  local repository="$2"
  local commit="$3"

  if [[ ! "$commit" =~ ^[0-9a-f]{40}$ ]]; then
    echo "$label commit is not a full SHA-1: $commit" >&2
    return 1
  fi

  if ! git ls-remote "$repository" | awk '{print $1}' | grep -Fxq "$commit"; then
    echo "$label commit is not advertised by $repository: $commit" >&2
    return 1
  fi
  echo "$label lock verified: $commit"
}

verify_commit \
  "CMFA" \
  "$(read_property cmfa.repository)" \
  "$(read_property cmfa.commit)"

verify_commit \
  "Mihomo" \
  "$(read_property mihomo.repository)" \
  "$(read_property mihomo.commit)"

