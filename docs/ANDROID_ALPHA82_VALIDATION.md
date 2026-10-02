# Android alpha82 — 2026-09-22

## This pass

- Common endpoint checks publish completed rows individually, with at most three simultaneous requests. DNS, TLS, timeout and connection failures have structured categories. Cancellation does not become a false failure; redirects require review instead of claiming final-site availability.
- Network revision changes cancel node-health work and invalidate cached evidence. Late results from the previous route cannot become the current result.
- User-triggered diagnostic summary preview and explicit clipboard copy. A structural allowlist excludes actual IP values, subscription/node names, credentials, fingerprints and raw exception messages. Clipboard access by other applications remains possible and is disclosed. No automatic report upload was added.
- Diagnostic cards now separate service, status and supporting evidence; the overview uses a two-by-two metric layout. Existing eight palettes remain. The card highlight is cached/static, not an animated blur or additional background polling loop.
- Added complete six-language UI strings for the new controls and failure explanations. The machine-readable diagnostic summary uses stable English field names.

## Validation

- 226 JVM tests: zero failures/errors, including concurrency, incremental results, cancellation, safe summaries and translation coverage.
- Debug lint: zero errors, 19 warnings, four hints; not a security certification.
- Debug, optimized ARM64 and instrumentation builds passed. Local-only release audit passed.
- Six UI regression tests passed during this pass: diagnostic preview/progress (two), subscription scrolling, WebView renderer recovery and theme rendering (two). The final diagnostic pair was rerun successfully after correcting the test-only screenshot directory.
- Bundled-core/provider tests (seven), encrypted subscription store tests (four) and real VpnService fixture traffic across automatic/fixed exits and restarts (one) passed in the isolated API 36 ARM64 emulator. Runner reports 13 tests including the deliberately unrequested stored-provider inspection, which is assumption-skipped; no real subscription credentials were inspected.
- Synthetic diagnostic screenshots were visually inspected. The emulator's System UI ANR initially obscured captures; after dismissing that system dialog, the preview and progress cards were reviewed without obstruction. This environment is unsuitable for performance benchmarking.
- Final optimized APK installed and launched successfully in the isolated emulator. Physical vivo installation/subscriptions were not modified in this pass.

## Package

- `dist/Weave-0.3.0-alpha82-arm64.apk`; versionCode 88; 19,209,171 bytes.
- SHA-256: `6f86c02ea4375fa73e5de1436c62fae81df6e06c7d8943425f3aea005997f712`.
- Non-debuggable optimized ARM64 build, signed with the existing local Android development certificate, not a production release key.
- Certificate SHA-256: `54271ffd8e45ca026f886b96a78a55fa97ff4175c7403f4938310047a4f784c2`.

## Limits

Real mobile/Wi-Fi handover, OEM background policies, long-session stability, high-refresh frame timing and battery comparisons remain hardware acceptance work. Bounded blocking network operations can continue until their timeout after coroutine cancellation; stale publication is rejected. HTTP entry responses do not establish content/account unlock or leak-free networking. No claim of complete anonymity, zero crashes or guaranteed 120 fps. No Android Release was published.
