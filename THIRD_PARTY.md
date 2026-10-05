# Third-party code and references

Weave vendors source-built `libclash.so` binaries for four Android ABIs and a `darwin/arm64` Mihomo
executable for macOS. The corresponding source
coordinates, toolchains, tags and artifact hashes are recorded in `core-lock.properties`; reproduction
instructions are in `docs/CORE_PROVENANCE.md`.

Design and architecture references:

| Project | Role | License / note |
|---|---|---|
| [Clash Meta for Android](https://github.com/MetaCubeX/ClashMetaForAndroid) | Android compatibility and Mihomo integration reference; candidate source pinned at `82b73a4bca24f1606e4b443bc9574cf1758c9693` | GPL-3.0 |
| [Karing](https://github.com/KaringX/karing) | Multi-subscription and rule-management product reference | GPL-3.0-or-later plus naming restriction in its license file |
| [Mihomo](https://github.com/MetaCubeX/mihomo) | Vendored native core built from submodule `e26714a181ac0e2fa803453c0a8e9a9ce94e31cb` | GPL-3.0; RC4 notices preserve the RC107 ARM64 linked-module SBOM and license collection |
| [MetaCubeX meta-rules-dat](https://github.com/MetaCubeX/meta-rules-dat) | Immutable lite GeoIP/GeoSite data bundled for optional China-direct routing; exact assets and hashes are in `geodata-lock.properties` | Generated data; retain upstream source attribution and audit each pinned release |
| [sing-box](https://github.com/SagerNet/sing-box) | Candidate second engine and Android package-name routing reference | Verify exact pinned revision before integration |
| [AndroidX](https://github.com/androidx/androidx) | Android UI and lifecycle libraries | Apache-2.0 |
| [CameraX](https://developer.android.com/jetpack/androidx/releases/camera) | Version 1.5.3; lifecycle-bound live QR preview and bounded image analysis, decoded locally with ZXing | Apache-2.0 |
| [ZXing](https://github.com/zxing/zxing) | QR generation and payload utilities | Apache-2.0 |
| [SnakeYAML](https://github.com/snakeyaml/snakeyaml) | Version 2.5, bounded data-only Clash YAML parsing with SafeConstructor | Apache-2.0 |

The [RC4 public prerelease](https://github.com/sundaysebasidian-byte/weave-client/releases/tag/v0.4.0-rc4)
provides `Weave-RC110-Corresponding-source.zip`: its exact Weave tree, pinned
CMFA/Mihomo sources, 119 Go module source archives, patches and build instructions.
GitHub's automatically generated source ZIP is not a substitute for this complete bundle.
Weave source, its CMFA patch, lockfiles and build instructions are available under the matching tag.
Source-availability obligations also apply to preview binaries, not just production releases.

`Weave-0.4.0-rc4-build110-Notices.zip` preserves the original RC107 runtime Maven
and ARM64 Go inventories, dependency copyright, licenses and NOTICE files. RC107
to RC110 dependency, core and geodata locks are unchanged; the inventories were
not regenerated. Preserve this exact bundle and corresponding source with redistributed
binaries. Coverage is Android ARM64, not all platforms or proof every class survives R8;
this is not legal or security certification.

`main` retains the alpha83 code line; the binary's corresponding Weave source is
the immutable `v0.4.0-rc4` tag at `908fbc16465a0495c5b28ece9b38b39e7ff0e3e8`.
