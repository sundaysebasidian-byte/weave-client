# Windows 0.4.2 RC3 — 2026-09-22

Installer: `Weave-Windows-0.4.2-rc3-x64-Setup.exe`, 58,202,443 bytes.

SHA-256: `6b5221cd85352faed4c23d600d75d1500ad342ed42fcfb25493288f06c43c0ac`.

Source: `2f5487eb6c20086ee0f50f1c72564d379b149360` on `codex/windows-v1-preview`.

Build: https://github.com/sundaysebasidian-byte/weave-client/actions/runs/35701421775

## This pass

- Typed DNS, TLS, timeout, connection and invalid-response outcomes. Request exception messages are not copied into diagnostic rows.
- User-triggered diagnostic summary preview and explicit clipboard copy. Export builds a structural allowlist of fixed endpoint names, status codes, timing, enum failure codes and address-presence booleans. Actual IP values, subscription/node names, fingerprints and raw errors are excluded. The summary is a snapshot, not proof of leak-free networking.
- Clipboard copy requests no history or cross-device roaming. The preview warns that other apps may read the clipboard. No automatic export or upload.
- Diagnostic result cards separate service name, readable status, HTTP/timing evidence and verification badge. Live language switching updates each bound field. Existing eight palettes and desktop navigation retained; no additional backdrop blur or continuous animation.

## Passed

- 80 tests; zero failures/skips. New tests cover allowlisted report output with deliberately private fixture strings, invalid status/timing bounds and typed failures. Existing bundled-core, fixture TUN, subscription and transfer tests remained enabled.
- WinUI build, startup, compact/wide layouts, Chinese/English pages and live language switching.
- Filled diagnostic page screenshots visually reviewed in Chinese and English. These explicitly labeled CI fixtures are synthetic, not real exit/availability measurements. Their code runs only when both explicit preview environment flags are set.
- Installer built successfully; downloaded SHA-256 matches the runner.

## Limits

Windows 10/11 hardware, real subscription protocols/IPv6, clipboard policies, UAC, tray behavior, sleep/wake, high-refresh performance and long-session recovery still require real-device acceptance. The installer remains unsigned. Do not disable Windows security protections. No Release was published or main merged; Android/macOS changes were excluded from these commits.
