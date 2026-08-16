# Contributing to Weave

Thank you for helping improve Weave. A VPN client handles sensitive network
traffic, so correctness and privacy take priority over feature count.

## Before opening a change

- Use an issue or discussion for a large behavioral or architecture change.
- Never commit a real subscription, node address, credential, QR code, signing
  key, provisioning profile, or unredacted device log.
- Keep user-visible entries functional. Do not add placeholder controls that
  imply an unsupported protocol, privacy property, or successful VPN state.
- Any new network endpoint, hosted dependency, updater, telemetry, or remote
  configuration must update the privacy notice, endpoint inventory, local-open-source
  profile, and release audit before it is merged.
- Preserve GPL and third-party attribution when adapting code.

## Local checks

Use JDK 17, Android SDK 36, NDK `29.0.14206865`, and CMake `3.31.6`:

```bash
./gradlew testDebugUnitTest
./gradlew lintDebug
./gradlew assembleDebug
```

Native core updates must follow `docs/CORE_PROVENANCE.md`, update every lock and
hash, include a dependency/license review, and pass the real-device VPN matrix.

## Pull requests

Explain the user problem, security/privacy impact, tests, and screenshots for a
visible UI change. Keep unrelated formatting out of the change. A maintainer
may request a reduced reproduction or additional leak/fail-closed testing.

By contributing, you agree that your contribution is licensed under
GPL-3.0-or-later and that you have the right to submit it.
