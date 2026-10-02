# Android experience preview validation — 2026-10-02

Candidate: `0.4.0-experience-preview1`, versionCode 102, Android ARM64, development signing.
Tested implementation commit: `5db7649` on `local/weave-experience-upgrade`.

## Provenance and protected work

The original project is `/Users/sebasidian/Documents/Codex/2026-07-29-cmfa-ui-karing-github`, original HEAD `d35b06ba477457d88e0952d9a3d904256c89348e`.
Its 77 tracked modifications plus relevant untracked source were preserved as a 175-file local snapshot, commit `633441f` on `local/weave-uncommitted-baseline`. All 175 original-file hashes still match. No original file was overwritten.

The candidate Android baseline is `57d665851a2bfa55146ca9794fbb8f2a391b85a0` (September 28 UTC), integrated locally as `b6efd6f`. The newer modular Android implementation replaces overlapping Android files; the original local Android edits remain recoverable in the frozen snapshot. Local Windows/macOS sources were retained rather than replaced with an older desktop branch. The old PR12 draft head `6e0390f0f1d39fc3c175e29658c79a37c4c52dd5` is not evidence of an October upgrade.

No other active Weave writer was found in the normal task inventory/project process checks. Claude finished before integration; final checks found no Claude process working in these Weave directories.

## Product changes

- Home: connection state, current exit, routing mode, then live data/checks; one clear primary action; missing/invalid exits have a selection action. Missing core disables connection. Errors show the actual reason, retry and change-exit actions. Connecting cannot be submitted twice. Only a verified exit is styled as positive.
- Settings: appearance/language, DNS/connection, protection and data first; advanced connection/routing/diagnostics use collapsible groups whose state survives restoration. Existing entries and callbacks remain reachable. Notification visibility and its explicit management action are shown.
- Subscriptions: executable add/migrate/LAN transfer empty state; progress and retry states; meaningful node/update/usage hierarchy.
- Shared design: consistent status tones, headings and 48dp+ actions; finite state, press and disclosure motion. Eight palettes and six languages retained; added translations and fixed dynamic settings summaries.
- Visual QA caught German error actions breaking a word at 1.3 font scale; they now use full-width stacked actions and have an accessibility regression assertion.

Claude collaboration was real: official Claude Code 2.1.286, existing signed-in claude.ai Pro session; isolated branch `local/weave-claude-frontend`, commit `a399381`. It changed the nine allowed frontend files using scoped file tools, with no shell/MCP/subagents. Codex reviewed, integrated and tested it. No secrets, new credentials or purchases were provided. Claude did not claim to compile or test.

## Reliability changes

- Replaced unreliable MODE_MULTI_PROCESS preference caches with explicit snapshots, a per-store process/file lock, key-level edit merge and AtomicFile writes. Existing XML types and encrypted values remain compatible. There is no Binder payload limit or exported settings provider.
- Runtime network/default-route reads use one coherent snapshot. Reused editors consume committed changes, avoiding replay over a newer process write.
- Notification policy handles first request, rationale, permanent denial and disabled channels; requests require an explicit settings action. Denial does not block VPN or silently start a connection. POST_NOTIFICATIONS already existed in the manifest.
- DNS and other foreground probes cancel on dialog exit/background; cancellation is preserved through coroutines, outdated generations cannot publish, and stopped DNS work does not produce a false aggregate failure.
- Routing rule state is now created before startup work, fixing an initialization-order null access exposed by immediate test scheduling.
- New cross-process QA service is debug-only, non-exported, and uses a dedicated process; it never starts VPN.

## Actual validation

Mac host, JDK 17, existing Android SDK 36, NDK 29.0.14206865/CMake 3.31.6; no toolchain downloads. Tests ran on a newly created isolated API 36 ARM64 emulator, `emulator-5570`.

- `assembleDebug` and `assembleDebugAndroidTest`: passed.
- `testDebugUnitTest`: 264 passed, 0 failures/errors/skips.
- `lintDebug`: 0 errors, 24 warnings, 5 hints. Warnings include SDK/dependency/style/optimization suggestions; this is not a warning-free build.
- Device instrumentation: 35 passed, 0 failed, 1 deliberate skip, 65.863 seconds. The skipped test is opt-in inspection of stored user providers; no user fixture was supplied.
- Cross-process tests: actual different PIDs, stale editor merge, 50 concurrent unrelated-key writes, reused editor behavior, legacy Android XML, all value types, backup recovery and 1.2 MB payload.
- Actual native core: parser/settings matrix, invalid/missing provider rejection, automatic/manual selectors, loopback transfer after teardown/reconnect, load balance readiness and temporary runtime cleanup.
- UI: three real primary navigation screens; retry/exit callbacks, disabled unavailable/connecting states, add/migrate/transfer, loading/retry, advanced group restoration, DNS cancellation, long subscription detail scrolling; eight palettes at German 1.3 font scale.
- Before/after screenshots are unedited 1080×2400 captures using the same empty English/default-theme QA fixture. Additional state screenshots are synthetic fixtures.

Failures found during work were corrected and rerun: the initial screenshot harness used the wrong navigation label; one test command used the wrong subscription-store package; key-event dismissal was timing dependent; full regression exposed the rule-state ordering defect. One startup run also ANRed during concurrent build/emulator CPU pressure; final runs passed with builds and device tests separated. One lint run failed internally while source was changing; the final stable-source lint passed.

## Deliverables and limits

The package includes the tested ARM64 APK, Android source/resources and tests, desktop text sources, source patch, test logs/summary, before/after/state screenshots and an HTML review.
Machine-local configuration, keys, other ABI binaries and desktop binaries/icons are excluded from the compact source package. The complete local candidate checkout retains those existing desktop/native resources.

Run `python3 prepare-arm64-source.py` in the package to restore the exact symbol-stripped native runtime from the APK, checked against CANDIDATE.json. The original unstripped source artifact and upstream coordinates remain separately pinned in core-lock.properties. Set your SDK path in source/local.properties and use the existing JDK17 toolchain:
`./gradlew -PweaveArm64Only=true assembleDebug assembleDebugAndroidTest testDebugUnitTest lintDebug`.

The APK is suitable for an isolated QA device or a compatible development-signature installation. Production signing/install-over-release compatibility was not verified.

No VPN permission was granted. Real Android VPN/TUN routing, live subscription/provider reachability, Windows 10/11 VPN rollback, desktop builds and refresh-rate performance were not verified in this round. No remote push, PR update, merge, deploy or publication occurred.

