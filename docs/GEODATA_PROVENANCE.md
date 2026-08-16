# Bundled Geo data provenance

Weave's “国内智能直连” mode is enabled by default and uses immutable GeoIP and GeoSite files bundled in the APK.
The app does not download or silently replace these files at runtime.

The exact source coordinates and SHA-256 digests are recorded in
[`geodata-lock.properties`](../geodata-lock.properties). The current files came from the official
MetaCubeX `meta-rules-dat` GitHub release tagged `latest`, resolved to commit
`4178770badecb1b349fbcd62c737e0d7a2079729` and published at
`2026-07-29T23:31:01Z`.

| APK asset | Upstream asset ID | Bytes | SHA-256 |
|---|---:|---:|---|
| `GeoIP.dat` | `494658274` (`geoip-lite.dat`) | 207159 | `1e49d985b16d13f3407d43582af64e0431c76e204a97460e5a8f859537687d13` |
| `GeoSite.dat` | `494658312` (`geosite-lite.dat`) | 176215 | `b2c9500f8e3403126a99f47bd9a5bced435c04316823b914bab6d5ee639e8cb7` |

At core initialization, `BundledGeodataInstaller` verifies those digests and atomically copies the
files into Mihomo's private data directory. A modified or truncated APK asset is rejected before
the VPN runtime is started.

The lite dataset is intentionally small. It improves practical China-direct routing, but it is not
a promise that every Chinese domain or address is classified. Explicit per-app routing stays ahead
of Geo rules, and the final catch-all route remains the user's selected proxy or direct target.
