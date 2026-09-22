# Android alpha83 — 2026-09-22

## Changes

- Diagnostic endpoints occupy fixed keyed slots before, during and after testing. Pending results are explicitly unmeasured, with no shimmer or new continuous animation. Stable minimum card height reduces movement as details arrive; long translated text may still expand for accessibility.
- DNS/TLS/transport failures show no response, rather than implying that the service imposed a regional restriction. Redirects require review. Existing palettes/material effects are retained.
- Cancelling a refresh clears partial progress and marks retained prior reports historical. Closing a completed report alone does not invalidate it. The copied-summary acknowledgement resets when its underlying report changes.
- Policy-pack import, validation, encrypted save/list, enable/disable and deletion now run on Dispatchers.IO. The existing running guard serializes UI mutations. Failures keep the visible list and show a generic localized message instead of exposing raw provider exceptions.

## Checks

- 229 JVM tests passed, zero failures/errors. Three new tests cover cancelled evidence and completed-report preservation.
- Debug lint: zero errors, 19 warnings and four hints. Local release audit passed; neither is a security certification.
- Debug, optimized ARM64 and instrumentation builds passed.
- Three diagnostic UI tests passed on the isolated API 36 ARM64 emulator, including a position assertion: completing an earlier endpoint does not move the observed YouTube slot. Preview/cancel behavior and disabled copy while running also passed. Fixtures are synthetic, not real network measurements.
- The emulator System UI displayed its recurring ANR overlay. It was dismissed before repeating screenshots/tests; do not use this emulator session as a frame-time benchmark.
- Final optimized APK installed and launched in the emulator; versionCode 89, versionName 0.3.0-alpha83, live process, no crash-buffer entries at inspection. Physical phone was not modified.
- Network core/configuration unchanged in this pass. Actual OEM background behavior, policy-provider latency, real subscriptions, long sessions, battery and 120 Hz performance remain hardware validation work.

## APK

`dist/Weave-0.3.0-alpha83-arm64.apk` — 19,210,563 bytes.

SHA-256: `db122b55c7eead45180076ebf4cbba88e55a7b7d5e67f39c2d1aff18c53e0647`.

Signed using the existing local development certificate, SHA-256 `54271ffd8e45ca026f886b96a78a55fa97ff4175c7403f4938310047a4f784c2`. This is an optimized local test build, not a production-signed public release.
