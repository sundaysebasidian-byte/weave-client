# RC109 device feedback audit

RC109 (versionCode 109, 0.4.0-rc3) is isolated from production RC108, source `0b3877d14e61e8687acf2852214f23f714d604cd`. Existing RC107/RC108 artifacts and production signing identity are preserved. This branch has no remote publication, merge, deployment or real-phone operation.

## Device evidence and installer issue

All three Library JPEGs were materialized through the current Library workflow and inspected as pixels. Their contents establish three separate observations:

- The default-exit picker spends too much vertical space on each node, with a separate untested status and a repeated large selected surface.
- LAN export and import occupy one long dialog, which obscures the subscription selection and primary action.
- The system installer displays **“App registration verification failed”**, with app-registration and Appeal wording. This is a system registration check, not an in-app VPN or node-probe result. The screenshot alone does not establish the device vendor, registration status or exact system cause.

RC108's production APK was independently verified before this feedback: application `io.weave.client`, build108, one production signer SHA-256 `1c8f2258965d95784c7a9756119ced127bdbcee2c21867de2c1abbd483fac4e1`, APK SHA-256 `044b2404ce45bc891c58c2f69e3db7d9b20a504d37b3376886515eaa548f8330`. Cryptographic verification does not establish app-registration acceptance. Re-signing is not claimed to fix registration. No security-check bypass, registration filing, identity submission or device-security setting change is included.

## Authorized UI collaboration

The existing official Claude Code 2.1.289 login uses the first-party provider and existing Pro allowance. Both the initial verification and UI run identify `claude-opus-5-5`, effort high. Claude owns the independent `ui/opus55-rc109` worktree; local integration, artifact checks and regression tests belong to the candidate branch. Only necessary project code and sanitized feedback descriptions were shared. The real node/subscription names in the user images were not sent to Claude. No credentials or raw user backups were shared.

The first UI run hit the explicitly configured 55-turn limit. This is recorded as an incomplete run, not successful completion. The same official session was continued with a bounded finishing task.

The continuation completed the main patch. A narrow follow-up refined the remaining reachable recovery, policy-pack and route-explanation dialogs; the private legacy IP-quality dialog was not a reachable surface. Main home/subscription navigation and the established palette remain unchanged. Integration added an explicit Done action to the LAN numeric keyboard, a single-line short field label with the full instruction below it, and balanced heading line breaks after inspecting native large-font captures.

## Other-client subscription selection

The user's named source families are CMFA, Karing and x2ray. The verified capability boundary is:

| Source | Official Android identity/interface | RC109 behavior | Limit |
| --- | --- | --- | --- |
| CMFA | `com.github.metacubex.clash`, `.meta`, `.alpha`; exported permission-protected `<package>.files` DocumentsProvider | User selects CMFA's config directory through the system picker; Weave lists the real UUID config documents and selects one/many (max20), previews and atomically saves encrypted records | Official provider exposes config snapshots, not original subscription URLs. Imports are local snapshots; no automatic URL updates or routing/DNS/group migration. Read grant is required; no private-store fallback. |
| Karing | `com.nebula.karing`; non-exported FileProvider with share URI grants; official Backup and Sync ZIP | Verified v1.2.25.2802 official backup ZIP -> real group catalogue -> select one/many -> preview -> encrypted snapshot import | No public list provider established. One official backup export/share is required first. Only compatible node options are imported; unknown options disable the group. Source subscription URLs/routing/settings are not copied. |
| x2ray | The name does not uniquely establish the user's Android package/project. One public listing is HexaSoftware `dev.hexasoftware.xmaster`; it does not establish the user's app | No native subscription list support claimed | Not substituted with v2rayNG. No verified migration interface/schema for the user's app. |

The CMFA adapter uses document IDs that match the pinned upstream `FilesProvider` and `document/Picker`, and copies only safe granted `providers/...` cache files. Unknown paths and nested providers fail validation. An unreadable profile is visible but disabled. Only selected config contents are opened. Entire selected batches are normalized before any store write; encrypted files are staged and one AtomicFile-backed preference transaction publishes their indexes. Source handles and reviewed payloads expire after five minutes and confirmation is single-use.

CMFA's pinned public source is `94ebfd648abae76d32ae2191cfc413482d654c66`; Karing main is `9d28b22fbbcca5818d147629aae151d49d4dcb7b`. Karing official Android release `v1.2.25.2802` was downloaded from its public GitHub release and verified against SHA-256 `b02cd70ce575942967467b56df20c8cb084bb7519f64a67567d87ff2e39458a3`. Only synthetic localhost fixtures are used on the isolated emulator; no source-app private files or real subscriptions are read. Legacy backup helpers identify `karing_subscribe.json`; the actual serialized items/groupid/remark/servers structure was confirmed through the official sharesheet on the isolated emulator. The sanitized official-shape fixture is included in JVM and Android integration tests. The release also uses the non-exported `com.nebula.karing.flutter.share_provider` for explicit share URI grants.

Official sources: [CMFA](https://github.com/MetaCubeX/ClashMetaForAndroid), [Karing](https://github.com/KaringX/karing), [Karing backup documentation](https://karing.app/en/tutorial/backup-sync), [Android sandbox](https://source.android.com/docs/security/app-sandbox), [X2Ray VPN publisher listing](https://play.google.com/store/apps/details?id=dev.hexasoftware.xmaster).

The additional official Opus 5.5 high run completed the source chooser, real catalogue checkboxes, selection cap/count, batch preview, explicit confirmation, individual saved-subscription open actions and capability notes, with six-language copy. Root wired the CMFA callbacks and owns backend validation. Manual compatible file/text/QR input remains a clearly identified secondary import method.

## Validation scope

Instrumentation uses the explicitly addressed isolated ARM64 emulator, with a `ranchu`/`sdk_gphone` guard before changing display/font settings. Production Compose components use synthetic subscriptions and offline callbacks. Native screenshots come from Android's compositor after a rendering wait.

The native node matrix covers 320/360/411dp × font1/1.3/1.5/2 × six languages. The LAN matrix covers actual 360dp/font1 and 320dp/font2 × six languages, including selected IDs, independent confirmation, running-state controls, all six exported code digits and passive mode switches. Before/after LAN comparisons share 360dp/font1 English and 320dp/font2 German. LocalDensity-only legacy cases remain explicitly distinguished from actual Android configuration.

No test starts VPN, opens real source-client data, contacts real subscriptions or transfers data to a LAN peer. Karing official export acceptance was checked with synthetic localhost data on the isolated emulator. Owner phone acceptance, actual CMFA provider/SAF acceptance, live LAN peer exchange, app-registration acceptance and Windows10/11 VPN rollback remain outside these Mac/emulator results. Final test counts and screenshot paths are recorded only after integration and execution.
