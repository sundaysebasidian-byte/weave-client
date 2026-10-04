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

The actual source client and version remain required to implement a truthful adapter. The inspected screenshots contain only Weave and the system installer. Detecting an installed app does not grant access to its private subscription store. The intended flow is a supported export/share operation, an actual subscription list, multi-selection, preview and selected import. No mock list or private-database extraction is included.

Pinned public-source research found:

- v2rayNG's examined backup implementation calls `MMKV.backupAllToDirectory` and zips the result; this is a binary MMKV backup, not plain subscription JSON. Its FileProvider grants only shared files. The existing node/config parser is not presented as a v2rayNG backup reader.
- FlClash's examined backup implementation writes config JSON, a database snapshot and `profiles/<id>.yaml`. A selected, user-exported archive could support a real subscription picker once the actual client/version and schema are confirmed.
- The examined Hiddify repository interface manages private profile metadata and profile files. No public cross-app subscription-list API was established from that interface.

The public source hashes and exact paths are retained in the candidate evidence. Android's app sandbox is documented at https://source.android.com/docs/security/app-sandbox. The source-client question does not block the independently authorized UI work.

## Validation scope

Instrumentation uses the explicitly addressed isolated ARM64 emulator, with a `ranchu`/`sdk_gphone` guard before changing display/font settings. Production Compose components use synthetic subscriptions and offline callbacks. Native screenshots come from Android's compositor after a rendering wait.

The native node matrix covers 320/360/411dp × font1/1.3/1.5/2 × six languages. The LAN matrix covers actual 360dp/font1 and 320dp/font2 × six languages, including selected IDs, independent confirmation, running-state controls, all six exported code digits and passive mode switches. Before/after LAN comparisons share 360dp/font1 English and 320dp/font2 German. LocalDensity-only legacy cases remain explicitly distinguished from actual Android configuration.

No test starts VPN, opens real source-client data, contacts real subscriptions or transfers data to a LAN peer. Owner phone acceptance, actual source-client export acceptance, live LAN peer exchange, app-registration acceptance and Windows10/11 VPN rollback remain outside these Mac/emulator results. Final test counts and screenshot paths are recorded only after integration and execution.
