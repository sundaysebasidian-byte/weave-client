# Android Compose localization workbench

These catalogs are the source for the sealed Compose assets in the Android app.
The language picker offers Arabic, Spanish, Korean, Russian and Hindi when the
matching APK catalog passes its integrity check. Native Android surfaces use
the string resources in the corresponding locale.

- `source-static.jsonl`: 1,309 distinct source phrases from the merged English
  catalog on the selected RC6 baseline. The ID is the exact legacy Simplified
  Chinese runtime key. Check this file against the real Kotlin catalog with
  `ComposeStaticLocaleInventoryTest`.
- `source-rc6-delta.jsonl`: 12 keys from the expanded RC6 transfer/network and
  confirmation work, 5 RC7 in-app update install keys and 7 diagnostics and
  route search keys. Kept separate so existing keys retain their order.
- `source-dynamic.jsonl`: 146 runtime templates, fragments, separators and
  compositions. Regex IDs include branch position, so review only that pattern
  family if the Kotlin branch structure changes. Existing precedence is untouched.
  One duplicate ordered matcher is unreachable and excluded. Recheck via
  `python3 tools/inventory-android-dynamic-copy.py --check`.
- `ar.jsonl`, `es.jsonl`, `ko.jsonl`, `ru.jsonl`, `hi.jsonl`: UTF-8 lines with `id` and `text`, in the
  order of the source files above. Different languages can be edited independently.
  No empty/English filler rows. The reference text in a source file is never
  substituted for a missing entry.

Run `python3 tools/check-android-compose-locales.py` to check the staged lines
for duplicate/unknown IDs, order, placeholders, exact technical identifiers
and bidirectional controls. Run with `--release` to fail on any missing ID.
The tests intentionally exercise missing keys, dropped placeholders, copied
English, changed technical names and bad bidirectional controls. The checks
are mechanical, not a substitute for review in context.

## Runtime activation, once the gate and independent review pass

Migrate translated static rows as an indexed catalog looked up using the same
exact source key; user data never goes through this map. Use existing recognizer
ordering for dynamic rows and pass named values to a single substitution pass.
Never localize a publisher/app label, URL, package ID, raw code output, version
or currency code as if it were product copy. Use count-based local phrasing
or full CLDR plural variants where appropriate; do not paste the English
`(s)` pattern. For Arabic, group numeric ranges/protocol symbols that must
stay left-to-right, and isolate untrusted inserted labels independently from
the surrounding text.

Add selectable language entries and Android per-app locale configuration only
after the catalogs cover actual core/transfer additions, the renderer has
instrumentation coverage and the complete device walkthrough passes. Include
Arabic/Spanish switching on Android 12, dark/light, large fonts and mixed
script. See `docs/qa/android-app-picker-locales.md`.

The host-only preview bridge is `localizeStagedWeaveText` backed by
`StagedComposeLocale`. It retains the existing ordered recognizers and nested
composition, renders named placeholders in a single pass, and throws on a
missing recognized key rather than returning English. Its tests load the actual
JSONL files, format the complete static/dynamic inventory for all five target
locales and exercise registry precedence with representative real inputs.
Existing locale paths still use their original definitions. This does not
establish Compose layout or device-level locale correctness.
