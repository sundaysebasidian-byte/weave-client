# Android alpha79 — local optimization and validation

Scope: Android only. No Windows/macOS source changes, Git push, or Release publication in this pass.

## Implementation order

1. Network recovery, foreground telemetry, subscription consistency, and configured routing explanation.
2. Compile and full Android unit-test regression. Fixed an unavailable LinkProperties copy constructor; the callback now keeps immutable public-field snapshots.
3. Diagnostic evidence scope/time, staged runtime restoration, allowlisted support summary, and node-picker correctness/state retention.
4. Unit tests, lint, local-release audit, and optimized ARM64 APK build. Lint caught Android 8/9 access to the API-29 MTU getter; added a version guard.

## Behavior changes

- Actual changes to the preferred network's link addresses, routes, DNS or supported MTU trigger existing debounced recovery. Identical callbacks do not. No background probe endpoint was added.
- Short dashboard visibility intervals are cancelled before querying the native core (350 ms settling window). Existing hidden/background suspension and idle backoff remain intact. Theme rendering is unchanged.
- Missing manually selected exits are never silently replaced by automatic selection. Invalid application targets are blocked and labelled for reselection. Invalid defaults retain their reference so strict configuration validation can reject them.
- Import preparation checks materialized root/provider node totals and normalization consistency before persistence. Existing failed-update retention remains enabled.
- Connection records show configured exits and can populate the routing explanation. They are **not** final native rule-hit evidence.
- IP, endpoint and WebView results display measurement time. Retained results during refresh are explicitly historical. WebView results are not generalized to Chrome or other applications.
- Disconnected, connecting and failed runtime states no longer claim that saved DNS/IPv6/STUN filtering is active.
- Runtime restore copies into staging before directory replacement, preserving the current directory if the copy fails. Existing rollback/candidate snapshots remain independent.
- Recovery summary export includes only version, Android API, safe-mode state, bounded failure count, presence of prior success, and a bounded diagnostic code. Free-form error text, URLs, addresses, credentials, subscription names and raw logs are excluded.
- Health results match exact raw provider names, not simplified decorative labels. Ambiguous raw names return no measurement instead of assigning another node's result.
- Picker search/selection survives UI state restoration; recovery content scrolls for small screens and large fonts. Added text has translations for all six supported languages.

## Boundaries

- USB inventory was empty during this pass. No installation, real subscription fetch, long-session handover test, battery measurement, or frame-time measurement was performed.
- Unit tests use synthetic provider documents (including 65-node root/provider fixtures); they do not establish the live Walless subscription's current node count.
- No claim of zero leaks, zero bugs, fixed FPS, measured power savings, or third-party security certification.
- The local optimized APK uses the existing local development certificate. Data-preserving update requires the installed app to use that same certificate. Do not uninstall to bypass a signature mismatch.

## Final local results

- `:app:testDebugUnitTest :app:lintDebug :app:assembleLocalOptimized -PweaveArm64Only=true --no-daemon --no-configuration-cache`: BUILD SUCCESSFUL on final source.
- Unit tests: 214, zero failures/errors/skips. Lint: zero errors, 19 warnings, two hints; warnings include existing dependency/target upgrades and native-loading/style advisories, not a security certification.
- `tools/audit-local-release.sh` and `git diff --check`: passed.
- APK: `dist/Weave-0.3.0-alpha79-arm64.apk`, versionCode 85, ARM64 only, optimized/non-debuggable, 19,185,278 bytes.
- APK SHA-256: `18d4f119c4ffa664b7757746ca573a2994a3623c988c1cab47667b153223cf58`.
- Signature verification passed. Certificate SHA-256 matches the prior local alpha78 APK: `54271ffd8e45ca026f886b96a78a55fa97ff4175c7403f4938310047a4f784c2`.
