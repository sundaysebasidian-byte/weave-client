# Windows dependency notices

- Weave: GPL-3.0, repository LICENSE. The installer includes LICENSE-Weave.txt.
- Mihomo v1.19.30: GPL-3.0; [source and license](https://github.com/MetaCubeX/mihomo/tree/v1.19.30). Unmodified official amd64-v1 binary; archive hash is documented in README and checked by CI. Installer includes LICENSE-Mihomo.txt.
- YamlDotNet 16.3.0: MIT; [source/license](https://github.com/aaubry/YamlDotNet/tree/v16.3.0).
- ZXing.Net 0.16.10: Apache-2.0; [source/license](https://github.com/micjahn/ZXing.Net). QR decoding and generation, entirely local.
- Microsoft Windows App SDK 1.6.250205002 and bundled WinUI/WebView2 SDK dependencies: [Windows App SDK](https://github.com/microsoft/WindowsAppSDK), [WinUI](https://github.com/microsoft/microsoft-ui-xaml), [WebView2 terms](https://www.nuget.org/packages/Microsoft.Web.WebView2). Installed Edge WebView2 Runtime is not downloaded or redistributed by this app.
- .NET 8 self-contained runtime: [source and third-party notices](https://github.com/dotnet/runtime/tree/v8.0.0). Preserve runtime license/notice files in distributions.
- Optional China routing data: MetaCubeX/meta-rules-dat, fixed commit `4178770badecb1b349fbcd62c737e0d7a2079729`, reuses Android's lite GeoIP/GeoSite assets without modification. See bundled geodata-lock.properties for exact URLs, hashes and provenance. [Upstream](https://github.com/MetaCubeX/meta-rules-dat/tree/4178770badecb1b349fbcd62c737e0d7a2079729).

This preview does not represent a completed transitive dependency/SBOM or licensing audit. Retain upstream attribution and applicable licenses when redistributing.
