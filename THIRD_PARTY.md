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
| [Mihomo](https://github.com/MetaCubeX/mihomo) | Vendored native core built from submodule `e26714a181ac0e2fa803453c0a8e9a9ce94e31cb` | GPL-3.0; transitive Go dependency SBOM still required before production release |
| [MetaCubeX meta-rules-dat](https://github.com/MetaCubeX/meta-rules-dat) | Immutable lite GeoIP/GeoSite data bundled for optional China-direct routing; exact assets and hashes are in `geodata-lock.properties` | Generated data; retain upstream source attribution and audit each pinned release |
| [sing-box](https://github.com/SagerNet/sing-box) | Candidate second engine and Android package-name routing reference | Verify exact pinned revision before integration |
| [AndroidX](https://github.com/androidx/androidx) | Android UI and lifecycle libraries | Apache-2.0 |
| [CameraX](https://developer.android.com/jetpack/androidx/releases/camera) | Version 1.5.3; lifecycle-bound live QR preview and bounded image analysis, decoded locally with ZXing | Apache-2.0 |
| [ZXing](https://github.com/zxing/zxing) | QR generation and payload utilities | Apache-2.0 |
| [SnakeYAML](https://github.com/snakeyaml/snakeyaml) | Version 2.5, bounded data-only Clash YAML parsing with SafeConstructor | Apache-2.0 |

The alpha77 binary release provides the pinned CMFA and Mihomo source archives alongside APKs.
Weave source, its CMFA patch, lockfiles and build instructions are available under the matching tag.
Source-availability obligations also apply to preview binaries, not just production releases.
A complete Go dependency SBOM and consolidated copyright/NOTICE bundle remain required before a
production release; this preview is not a compliance or security certification.
