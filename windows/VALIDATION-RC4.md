# Windows 0.4.3 RC4 — 2026-09-22

Source: `4784f999d70f1d385d21680f002e5b0be0ebba52` on `codex/windows-v1-preview`.

Build: https://github.com/sundaysebasidian-byte/weave-client/actions/runs/35704024454

## Changes and validation

- Fixed diagnostic slots preserve order as results settle. Pending rows cannot be counted as measured, verified or exported. Duplicate/unknown results do not inflate progress. The final completion no longer replaces the whole list.
- Cancelled checks retain partial evidence as historical/uncompleted; a deliberate stop no longer displays unrelated administrator/firewall advice. New runs clear the previous timestamp. Redirect details retain their HTTP code without claiming reachability.
- Consistent result-card minimum height; existing glass style, eight palettes and navigation preserved. New pending status is bilingual.
- 83 core tests passed, zero failures/skips. Added fixed order/duplicates, pending-summary filtering and redirect detail tests.
- WinUI build and packaged startup passed, with compact/wide layouts, themes, Chinese/English pages and live language-switch checks. Diagnostic fixture screenshots reviewed in both languages; these are synthetic CI rows, not actual network tests.
- Downloaded installer SHA-256 matches the runner output.

## Artifact and limitations

`Weave-Windows-0.4.3-rc4-x64-Setup.exe`, 58,192,031 bytes.

SHA-256: `b929609e77b46c5e4734c3348470e8c508b647353b51477b8cf3fb1104107c29`.

Installer remains unsigned. Windows 10/11 hardware TUN/UAC/tray, real subscription protocols/IPv6, sleep/wake, long-session reliability and high-refresh performance require user-device acceptance. No Release was published or main merged; Android/macOS changes were not committed in this Windows branch update.
