# Open-source release checklist

An unchecked item is a release blocker unless the release is explicitly marked as
an unsigned developer preview.

## Source and licensing

- [ ] Tag exactly the commit used for every binary.
- [ ] Generate SPDX or CycloneDX SBOM for Android, Go core, and macOS.
- [ ] Generate the complete copyright and third-party license bundle.
- [ ] Publish corresponding core source/patches/build instructions next to binaries.
- [ ] Verify `core-lock.properties` and `geodata-lock.properties` hashes.
- [ ] Run a clean-history secret scan.

## Build integrity

- [ ] CI unit tests, lint, release/R8 build, and macOS tests pass.
- [ ] Build from a clean checkout using documented toolchains.
- [ ] Sign Android AAB/APK with the production lineage.
- [ ] Sign and notarize macOS artifacts with the intended entitlement profile.
- [ ] Publish SHA-256, native symbols, R8 mapping, SBOM, and provenance attestation.
- [ ] Make the GitHub release immutable after all assets are attached.

## Runtime validation

- [ ] IPv4, IPv6, dual-stack, DNS and WebRTC leak tests pass.
- [ ] Wi-Fi/mobile switching, Doze, lock screen, always-on, and kill switch pass.
- [ ] Per-app fixed/automatic/direct/block routes match the actual observed exit.
- [ ] 100 connect/reload/disconnect cycles leave no TUN, service, port, or plaintext.
- [ ] Test current Pixel, Samsung, Xiaomi/Redmi, OPPO/OnePlus, and vivo firmware.

## Policy and communication

- [ ] Privacy notice matches the exact build and all bundled SDK behavior.
- [ ] GitHub private vulnerability reporting is enabled and tested.
- [ ] Store VpnService declaration, Data Safety form, screenshots, and review video match.
- [ ] Release notes identify known limitations and migration/rollback behavior.
