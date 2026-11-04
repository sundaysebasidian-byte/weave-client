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

# These synthetic public test identities are never packaged in the app. Each exact
# path and fingerprint is reviewed; there is no directory-wide credential exemption.
test_key_globs=()
while read -r test_key_sha256 test_key; do
  if [[ -f "$test_key" ]] && [[ "$(openssl dgst -sha256 "$test_key" | awk '{print $NF}')" == "$test_key_sha256" ]]; then
    pass "public TLS fixture key matches its reviewed fingerprint: $test_key"
    test_key_globs+=(--glob "!$test_key")
  else
    fail "public TLS fixture key is missing or changed: $test_key"
  fi
done <<'PUBLIC_TEST_KEYS'
f337722a69f245ca3541271ab47f6d77ec170f0518348d69e35f715cdd9ebf01 tests/fixtures/https/test-only-key.pem
7f1fcb806c929fe89dae2dd8e5b62324a3165900865138d89defed52dee5b2c5 tests/fixtures/provider-materials/rsa-pkcs1.pem
7b67279a3a9b5c8059918494f9b017df10fecf7e1f64981da67bf8fd23980621 tests/fixtures/provider-materials/rsa-pkcs8.pem
8f72113d7775925e29a60eb833de06698376bbb13dc9351601042bf3eac1db74 tests/fixtures/provider-materials/ecdsa-sec1.pem
bb3648e3ea34fcfb07ecfda3295a3f311c0b4641f0c4d2b5260de5dd604fcb46 tests/fixtures/provider-materials/ecdsa-pkcs8.pem
9930f08ea2a4617c1ad619fc20a4db63fc49ea7826916e46a068b68593dc078d tests/fixtures/provider-materials/ed25519-pkcs8.pem
f0950c52d9f1e8a0c3aa03e1a9054aa89d3f825e11eec610e4a57edbba59ef3e tests/fixtures/provider-materials/ssh-encrypted.pem
PUBLIC_TEST_KEYS

if scan_output="$(rg -n \
  'BEGIN ((RSA|EC|DSA|OPENSSH|ENCRYPTED) )?PRIVATE KEY|ghp_[A-Za-z0-9]{20,}|github_pat_[A-Za-z0-9_]{20,}|AIza[0-9A-Za-z_-]{20,}|sk-[A-Za-z0-9]{20,}' \
  . --glob '!**/.git/**' --glob '!**/build/**' --glob '!**/.build/**' --glob '!**/src/test/**' --glob '!**/Tests/**' \
  "${test_key_globs[@]}" \
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
