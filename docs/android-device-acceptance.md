# Isolated Android device checks

Build the debug app and its instrumentation APK with a separate application ID:

```sh
./gradlew -PweaveArm64Only=true -PweaveDeviceTest=true \
  :app:assembleDebug :app:assembleDebugAndroidTest
```

The app is `io.weave.client.acceptance` (`Weave Device Test`); its runner is
`io.weave.client.acceptance.test/androidx.test.runner.AndroidJUnitRunner`.
Android assigns separate app storage and a separate Keystore namespace. Launcher
shortcuts are empty because the production shortcuts explicitly target the owner's
`io.weave.client` installation. Release and normal debug builds are unchanged.

Before installation, inspect both APK manifests and confirm the instrumentation
target. Refuse to replace an existing acceptance package unless its identity and
ownership have been verified. Use an explicit ADB serial and Android user; do not
clear data or replace the owner's app. Preserve APK hashes and instrumentation
output. A successful ADB exit alone does not prove tests passed: require the final
`OK (N tests)` and check failure, crash and skipped-test status codes.

Safe offline coverage includes the isolated storage/import/backup suites,
`ProductExperienceUiTest`, `PrivacyRendererRecoveryTest`,
`MihomoStartupConfigurationTest`, and the synthetic methods in
`MihomoProviderIntegrationTest`. Do not enable its stored-provider inspection.
These core checks use local fixtures and do not establish a system VPN.

Some OEMs freeze instrumentation processes that have no visible activity. If the
process is reported frozen, bring the isolated debug activity to the foreground
without changing device power or security settings:

```sh
adb -s DEVICE shell am start --user 0 \
  -n io.weave.client.acceptance/androidx.activity.ComponentActivity
```

Keep the interrupted result and repeat the affected suite in the foreground.
Such a run verifies foreground behavior, not background survival.

`StartupRecoveryIntegrationTest`, `MihomoVpnServiceIntegrationTest`, and
`tools/test-android-storage.sh` retain their disposable-emulator requirements;
do not set those opt-ins on a personal phone. This profile does not authorize
VPN permission, network changes, or inspection of personal subscriptions.
Real VPN routing, background survival, network changes, minimum API coverage,
and signed release acceptance remain separate checks.
