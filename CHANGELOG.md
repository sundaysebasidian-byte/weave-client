# Changelog

This project follows semantic versioning while in alpha. Breaking storage or
configuration changes may still occur before 1.0.

## Unreleased

## 0.3.0-alpha26 — 2026-08-12

### Changed

- Removed the universal Android APK from normal ABI builds; release output is now split per CPU architecture.
- Replaced bundled ML Kit image QR decoding with ZXing to reduce package size and offline model overhead.
- Flattened the Android paper surface, softened borders/accent contrast, and reduced bottom navigation/card elevation.
- Lazy-loaded the installed-app catalog and reduced visible runtime polling from 1 second to 2 seconds, while
  retaining the session timer and avoiding unchanged dashboard emissions.

## 0.3.0-alpha25 — 2026-08-12

### Added

- Versioned, affirmative VPN data-path disclosure before first connection.
- In-app security/privacy, routing, LAN transfer, and open-source component details.
- GitHub contribution, security, privacy, CI, dependency update, and release files.
- Adaptive woven-ribbon application icon shared by Android and macOS artwork.

### Security

- VPN runtime failures no longer expose arbitrary native exception text to logs or UI.

## 0.3.0-alpha24 — 2026-08-12

- Improved node availability testing, latency ordering, and retained prior results.
- Refined the warm mineral visual system.
- Verified Android 17 arm64 installation and startup.
