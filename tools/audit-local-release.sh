#!/usr/bin/env bash
set -euo pipefail

# Release guard for the local-open-source distribution profile.
# It is intentionally conservative and never proves legal compliance.

root_dir="$(cd "$(dirname "$0")/.." && pwd)"
cd "$root_dir"

failures=0
pass() { printf 'PASS  %s\n' "$1"; }
fail() { printf 'FAIL  %s\n' "$1" >&2; failures=$((failures + 1)); }

manifest="app/src/main/AndroidManifest.xml"
[[ -f "$manifest" ]] || { fail "Android manifest is missing"; }

if [[ -f "$manifest" ]]; then
  rg -q 'android:allowBackup="false"' "$manifest" && pass "Android backup is disabled" || fail "Android backup must be disabled"
  rg -q 'android:usesCleartextTraffic="false"' "$manifest" && pass "Android cleartext traffic is disabled" || fail "Android cleartext traffic must be disabled"
  if rg -q 'android:name="\.core\.vpn\.WeaveVpnService"' "$manifest" && rg -q 'android:exported="false"' "$manifest"; then
    pass "VPN service is not exported"
  else
    fail "VPN service must not be exported"
  fi
  if rg -q 'QUERY_ALL_PACKAGES|REQUEST_INSTALL_PACKAGES|SYSTEM_ALERT_WINDOW|READ_CALL_LOG|READ_SMS' "$manifest"; then
    fail "manifest contains a high-risk or broad-discovery permission"
  else
    pass "no broad package-discovery or unrelated high-risk permission"
  fi
fi

# Search dependency manifests for telemetry/update components. Runtime blocklists can legitimately
# contain the names of these domains, so scanning all source text would produce false positives.
dependency_paths=(app/build.gradle.kts macos/Package.swift ios/WeaveCore/Package.swift ios/WeaveIOS.xcodeproj windows/src)
if scan_output="$(rg -n -i \
  'firebase|crashlytics|sentry|appcenter|amplitude|mixpanel|posthog|adjust|sparkle|appcast|suupdater|remote.?config|check.?for.?updates' \
  "${dependency_paths[@]}" --glob '!**/build/**' --glob '!**/.build/**' 2>/dev/null)"; then
  printf '%s\n' "$scan_output" >&2
  fail "runtime/build inputs contain telemetry, crash-reporting, or updater markers"
else
  pass "no known telemetry/crash-reporting/updater markers in runtime inputs"
fi

if scan_output="$(rg -n \
  'BEGIN (RSA|EC|OPENSSH|PRIVATE) KEY|ghp_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|AIza[0-9A-Za-z_-]{20,}|sk-[A-Za-z0-9]{20,}' \
  . --glob '!**/.git/**' --glob '!**/build/**' --glob '!**/.build/**' --glob '!**/src/test/**' --glob '!**/Tests/**' \
  --glob '!docs/**' --glob '!tools/audit-local-release.sh' 2>/dev/null)"; then
  printf '%s\n' "$scan_output" >&2
  fail "possible private key or service credential found"
else
  pass "no common private-key or service-credential pattern found"
fi

for required in PRIVACY.md SECURITY.md THIRD_PARTY.md NOTICE docs/LOCAL_ONLY_RELEASE_PROFILE.md docs/NETWORK_ENDPOINT_INVENTORY.md; do
  [[ -f "$required" ]] && pass "release boundary file present: $required" || fail "missing release boundary file: $required"
done

if [[ "$failures" -ne 0 ]]; then
  printf '\n%d release audit check(s) failed. Review manually; this script is not a legal or security certification.\n' "$failures" >&2
  exit 1
fi

printf '\nLocal-only release audit passed. It does not establish legal compliance.\n'
