# Windows 0.4.1 RC2 — 2026-09-22

Installer: `Weave-Windows-0.4.1-rc2-x64-Setup.exe`, 58,205,502 bytes.

SHA-256: `7987dea5a37b1418ba4525934c531269822b7dc4ad178ce31f1490dcf05b7c63`.

Source: `549e26c6161e97024f57c86f4b6710a137d8c013`, independent `codex/windows-v1-preview` branch.

Build: https://github.com/sundaysebasidian-byte/weave-client/actions/runs/35699159268

## Implemented

- IPv4/IPv6 exit probes and website results settle progressively with bounded concurrency and cancellation. Redirects do not establish destination access. Network/session changes cancel old work and mark retained evidence historical.
- Browser experiments have a single-session guard, close on a context change, handle renderer/browser failures and retry cleanup of their own temporary profile. Cleanup failures are reported, not hidden. Third-party DNS and STUN tests still require consent and have expressly limited scope.
- Encrypted subscription, routing, preferences and system-proxy recovery records use staged, flushed replacement rather than truncating the current file.
- Subscription updates require a node-change preview. Store comparison rejects stale review or resurrection of a removed record. Missing fixed exits remain unresolved across restart, requiring an explicit new selection instead of silently becoming automatic.
- Reopening a tray-hidden instance locates and signals its own window; an exiting peer no longer aborts activation enumeration.
- Unified card radii, spacing, layered rims and restrained shadows. Removed per-card live Acrylic to reduce composition work; sidebar material remains. Eight palettes retained.

## Passed

- Windows-2022 build, packaged startup and installer generation.
- 77 core tests, zero failed/skipped. Includes atomic writes, stale subscription review, IP response validation, progressive diagnostics/cancellation, bundled Mihomo config/readiness, real automatic/manual HTTP forwarding and isolated fixture TUN IPv4 forwarding. Diagnostic website response tests use an injected HTTP handler, not public-site availability claims.
- Chinese/English page rendering, live language switching, compact/wide layout and selected themes. Light, dark, compact and English diagnostic screenshots visually reviewed.
- Initial CI attempt passed build/tests but exceeded its fixed four-second screenshot deadline on English page 2. Checks now wait for an explicit encoded-frame marker with a bounded 20-second deadline; the complete rerun passed. No validation step was disabled.
- Downloaded installer checksum matches the build runner.

## Still requires Windows 10/11 hardware

- Real subscription protocols and IPv6 forwarding; Wi-Fi changes, sleep/wake and long-running sessions.
- UAC relaunch, tray reactivation, firewall policy and recovery after forced termination with actual user proxy settings.
- Subscription review interactions and removed-node recovery with real imported data.
- High-refresh smoothness, CPU/memory and battery comparisons. Static rendering changes are not a measured performance improvement.

The installer is unsigned; the Inno Setup bootstrap is PE32 while the bundled application targets x64. Verify provenance/checksum and do not disable system protection. Closing the window keeps the app in the tray; use **Disconnect and quit** to stop it. This is not a system-level kill switch or proof of leak-free networking.

No Release or main-branch merge was performed. Android/macOS changes were not included in these commits.
