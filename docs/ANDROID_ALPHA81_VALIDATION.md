# Android alpha81 — 2026-09-22

## Changes from alpha80

- Evidence is accepted only for the current connected, TUN-ready or verified network revision. Recovery/waiting states cannot reuse a successful probe as current proof.
- IP, endpoint and download probes check context before publishing; a superseded download cannot overwrite the current report. Cancellation is preserved instead of becoming a network failure; fatal VM errors are not swallowed by the generic probe wrapper.
- Embedded privacy WebView rejects third-party cookies. Renderer termination releases the view, reports an interrupted/inconclusive test, and permits retry instead of taking down the activity. Release remains idempotent.
- Existing palettes and layout retained. Body text and line spacing improved; artistic card colour mixing and rim strength reduced; panels requesting no edge no longer draw an extra outline. No new continuous blur or background network polling.

## Validation

- 221 JVM tests: zero failures/errors.
- Debug lint: zero errors, 19 warnings, four hints. Remaining warnings include newer dependency/SDK notices, ARM64-only ABI scope, dynamic native loading and style suggestions; this is not a clean security certification.
- Debug, optimized ARM64 and instrumentation builds passed. Local-only release audit passed.
- Final optimized APK was installed and launched on the isolated emulator: version 87/alpha81, live app process, no crash-buffer entries at inspection. It is non-debuggable and disables backup/cleartext traffic.
- Isolated API 36 ARM64 emulator: privacy renderer callback recovery, 200-node list with 1.4× font/search, and theme rendering passed (four tests in the initial run).
- Expanded run: all eight palettes, seven real bundled-core provider tests, four encrypted subscription store tests and one real VpnService fixture test passed (15 tests). The TUN fixture covers automatic and fixed exits, each across start/stop/restart cycles, without real subscription credentials.
- Renderer recovery is a synthetic callback test on a real WebView, not a forced real renderer crash. Palette checks do not establish subjective beauty or 120 Hz performance.
- The emulator logged a System UI startup ANR (`com.android.systemui`, before this app launch); after dismissing that system dialog, the Weave home screen rendered and was visually reviewed. This host/emulator session is not suitable for frame-time comparisons.

## Package

- `dist/Weave-0.3.0-alpha81-arm64.apk`; versionCode 87; 19,203,403 bytes.
- SHA-256: `3226bbf3d87536889ce639e3f7eb6249cb98822083242656eebdf20fd3413863`.
- Signing certificate SHA-256: `54271ffd8e45ca026f886b96a78a55fa97ff4175c7403f4938310047a4f784c2`, same local development key as alpha80. Optimized does not mean production-signed.
- Existing physical vivo retains alpha80; this pass did not clear or replace its subscriptions, toggle its network, or install alpha81.

## Remaining hardware checks

Real mobile/Wi-Fi handover, Android 17 OEM background policies, sustained connection reliability, high-refresh frame timing and battery discharge comparisons remain unverified for this build. USB charging and a software-rendered emulator are not credible power/performance benchmarks. No claim of complete anonymity, zero leaks or zero crashes.
