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
| [Google ML Kit barcode scanning](https://developers.google.com/ml-kit/vision/barcode-scanning) | On-device QR image decoding | Google APIs Terms; distributed as a Maven dependency, not relicensed as Weave code |
| [Google Play services code scanner](https://developers.google.com/ml-kit/vision/barcode-scanning/code-scanner) | Optional system-provided camera QR scanner | Google APIs Terms; may download/use a Play services module |
| [ZXing](https://github.com/zxing/zxing) | QR generation and payload utilities | Apache-2.0 |

Before a production release, generate a complete Go dependency SBOM and copyright/NOTICE bundle, and
publish the exact corresponding source plus build instructions as required by GPL-3.0.
