# Android alpha80 validation

## Implemented

- Separate TUN readiness, waiting for an underlying network, recovery and successful user-triggered endpoint evidence. TUN creation alone no longer says the exit is verified.
- Increment connection-context revisions when runtime/network paths change. Cancel old privacy work, mark retained reports historical, and reject late HTTP/WebView/native-snapshot results.
- On-demand actual native connection snapshot through an application-private Unix socket, not an exposed TCP controller. Parent permissions are explicitly 0700. Only bounded app/protocol/rule-type/chain fields reach the UI, with a two-minute lifetime.
- Subscription update preview with added/removed names, affected routes, explicit review of suspicious count changes, five-minute expiration, and a store-level compare-and-swap check. Bulk refresh leaves node-set changes for explicit review. Repeated names are counted individually.
- Preserve existing theme rendering, foreground/scroll settling, background suspension and idle telemetry backoff. No new Android background Internet polling.

## Local validation

- Full unit tests: 220 passed, zero failures/errors.
- Debug lint: zero errors, 19 warnings, four hints (not a security certification).
- Optimized ARM64 build and local-release audit passed.
- APK: `dist/Weave-0.3.0-alpha80-arm64.apk`, versionCode 86, 19,202,555 bytes, non-debuggable optimized build using the existing development signing certificate.
- SHA-256: `c966d5aeb93bcb0d58bc0fa048a3e5f569f7860e9b2d43c6d99901617756bdbd`.
- Certificate SHA-256: `54271ffd8e45ca026f886b96a78a55fa97ff4175c7403f4938310047a4f784c2`, matches alpha78/79.

## Isolated emulator

- Fresh Android API 36 ARM64 emulator, no real subscriptions or accounts.
- Native provider + encrypted store integration: 11 tests passed. Real bundled core Unix controller accepted HTTP/1.0 snapshots across automatic/fixed configurations; 0700 parent permissions asserted.
- Real VpnService TUN test passed: automatic and fixed exits each started/stopped twice (four fixture forwarding cycles). Reserved documentation IP, local synthetic proxy, no remote credentials. VPN grant was limited to this disposable emulator.
- Updated store integration (four tests) plus 200-node/large-font scroll and search test passed (five tests in that run). A stale reviewed candidate was rejected without changing the newer payload; a fresh reviewed candidate committed successfully.

## Physical-device limits

- Authorized vivo V2359A found on USB with alpha78/versionCode 84 and an existing VPN session.
- Existing-session snapshot: PSS approximately 100.5 MiB; accumulated frame statistics p50 9 ms, p95 16 ms, 22/989 janky frames. These are not a controlled benchmark or measured improvement.
- USB charging prevents a credible discharge-energy comparison.
- Data-preserving alpha80 installation returned `INSTALL_FAILED_ABORTED: User rejected permissions`. No uninstall, data clear, physical network toggling or subscription replacement was performed. New-version physical handover, sleep/wake, sustained-session and frame profiling remain pending user unlock/installation consent.

Windows changes are committed separately on `codex/windows-v1-preview`. Android/macOS work was not pushed or released.
