# App picker: icon and language acceptance

## Coverage boundary

The app picker reads five strings from Android resources: title, search, empty
state, add action and close. App names, monograms and package IDs are user data:
they are not translated. The launcher icon comes from the same activity that supplied
the name. A known client without a launcher activity uses its application icon.
If an app disappears or its icon cannot be drawn, its existing monogram remains.

Korean, Spanish and Arabic resources include the native picker, notification,
shortcut, tile and widget **resource keys**. They are not advertised as complete
app languages. Most screens, including some text displayed by the widget, still
use the separate runtime catalog in `Language.kt` and `LanguageV2.kt`. Completing
the native resource set is not a substitute for translating those screens.

The picker only asks for visible rows as the lazy list composes them. Two workers
can rasterize icons at a time. Its in-memory cache has 32 slots (positive and
negative results), each thumbnail at most 128 × 128 ARGB pixels. The cache is
discarded with the dialog. No new package query, permission, storage or network
path is needed.

## Device acceptance (pending)

Use only a disposable device with fixture apps. Do not capture a real user's
installed-app list. Keep APK revision, API level, density, locale, font scale
and screenshot filenames with results.

1. Run `PickerLocaleAndIconTest` on API 28 and API 35+. It tests the packaged
   regional resources and quantity strings, and reads icons only for the QA APK.
   On the emulator, add a launcher alias with its own icon plus a legacy,
   non-adaptive icon. Verify alias artwork, aspect ratio and transparent edges.
2. In a fixture with a long app list, scroll and return, filter by package ID,
   clear the query, close and reopen. Removing a fixture app while the picker is
   open must leave a legible monogram, not a crash or a different app's icon.
   Tap a row and check that the matching package ID, not its label, is selected.
3. In a picker fixture with an explicit localized resource context and matching
   layout direction, capture ko-KR, es-MX and ar-EG in light/dark modes at normal
   and large system font sizes. Cover a long label, mixed Arabic/Latin text,
   a number, an empty result, an alias icon and a missing icon. Verify icon/name
   order, end-of-line clipping, search and close affordances, and the spoken add
   action. Arabic rows mirror; the spelling of package IDs does not reverse.
4. Before offering the languages in system settings or the in-app picker, finish
   the complete static **and dynamic** UI catalog. Audit all four tabs, onboarding,
   imports and updates, permission failures, diagnostics, and cross-process
   surfaces. Use locale-specific plural grammar and isolate inserted identifiers.
   Explicit in-app choice must control Compose layout direction even on Android
   12 and earlier. Change from Arabic to Spanish and back without changing the
   device language, including a cold launch; confirm layout and strings agree.

Host unit tests for the resource inventory and the icon cache are separate from
these device checks. They do not establish correct rendering or performance on
a phone.

### Staged Compose fixture (not a selectable app language)

On the isolated `.acceptance` APK, `StagedLocaleDeviceTest` reads the exact
catalogs from the instrumentation APK's assets. This test launches the real
picker and startup recovery UI with synthetic application data. It checks
Arabic mirroring, left-to-right package identifiers, a system-locale fixture
overridden in app and then restored, and long Spanish/Korean recovery labels
at 1.4× text scale. Its three picker screenshots go under
`qa/staged-locales` on the test device.

The test-only shared preference check covers reinitializing a preview context.
It is not proof of persistence across app process restart for the real product
preference. That remains a required device checkpoint once an enabled preview
build has the language-setting and Android per-app language integration.

`StagedLocalePersistenceTest` adds a real process-restart fixture: a private
preview value is committed in the existing debug settings-probe process,
that process exits, and a different PID reloads the value. It writes only to
the disposable APK's named test preference. It still does not exercise the
production Android per-app language setting.
