# Windows 0.4.0 RC1 validation — 2026-09-22

Installer: `Weave-Windows-0.4.0-rc1-x64-Setup.exe` (58,188,874 bytes).

SHA-256: `f9d97da310b61aa44a60fa03ddcc186e19d1e865f7fa3e6163f0fa1101a3d1cb`.

Source commit: `968acecea938bf75f8873ff1c064276909f7eb39` on `codex/windows-v1-preview`.

Build: https://github.com/sundaysebasidian-byte/weave-client/actions/runs/35693748449

## Passed

- Windows-2022 runner compiled WinUI/.NET, published the self-contained x64 app and packaged the Inno Setup installer.
- 61 tests passed, none skipped or failed. `WEAVE_TEST_CORE` points at the bundled hash-verified Mihomo, so native tests were actually enabled.
- Real imported HTTP proxy forwarding with authentication: automatic and manual exits. Isolated real Windows TUN IPv4 forwarding to TEST-NET-2 through the local synthetic upstream.
- Core configuration/readiness and process cleanup; synthetic 65-node imports; secure-transfer codec tests and Java Android wire-format interoperability.
- Chinese/English pages, live language switching, selected themes and compact/wide startup/render smoke checks. Main Chinese and English screenshots were visually reviewed.
- Downloaded installer SHA-256 matches the hash computed on the build runner.

## Awaiting the user's Windows machine

- Windows 10/11 hardware, real subscription protocols, IPv6, UAC prompt/relaunch, firewall policies, tray interactions, system proxy restoration, network changes and sleep/wake recovery.
- High-refresh frame timing and sustained CPU/memory/power measurements.
- An isolated TUN IPv4 CI test does not establish universal connectivity, IPv6 correctness, absence of leaks or zero bugs.

The installer is unsigned. Verify its source and checksum; do not disable Windows security protections. Closing the window leaves Weave running in the tray. To stop it, use **Disconnect and quit** in the tray menu. This is not an OS-level kill switch.

No Release was published and no Android/macOS changes were included in these Windows commits.
