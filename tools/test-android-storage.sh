#!/usr/bin/env bash
set -euo pipefail

root=$(cd "$(dirname "$0")/.." && pwd)
serial=${1:?Pass the serial of a fresh disposable emulator}
[[ ${WEAVE_ISOLATED_EMULATOR:-} == 1 && $serial == emulator-* ]] || {
  echo 'Storage tests require explicit opt-in to a disposable emulator.' >&2; exit 1;
}
adb="${ANDROID_HOME:?ANDROID_HOME must name the SDK}/platform-tools/adb"
port=${WEAVE_TEST_ADB_PORT:-5037}
target=("$adb" -P "$port" -s "$serial")
results=${WEAVE_TEST_RESULTS:-"$root/app/build/reports/storage-integration"}
mkdir -p "$results"
python3 "$root/tools/wait-android-ready.py" "$serial" | tee "$results/readiness.txt"
abi=$("${target[@]}" shell getprop ro.product.cpu.abi | tr -d '\r')
case "$abi" in arm64-v8a|armeabi-v7a|x86|x86_64) ;; *) echo 'Unsupported emulator ABI' >&2; exit 1 ;; esac
# Preserve native/runner crash diagnostics too: am instrument can exit successfully while
# reporting only "Process crashed". Start before APK installation to retain platform failures.
# This process belongs exclusively to this test invocation.
"${target[@]}" logcat -v threadtime -T 1 > "$results/logcat.txt" 2>&1 &
logcat_pid=$!
trap 'kill "$logcat_pid" 2>/dev/null || true; wait "$logcat_pid" 2>/dev/null || true' EXIT
"${target[@]}" install -r "$root/app/build/outputs/apk/debug/app-$abi-debug.apk"
"${target[@]}" install -r "$root/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
classes=io.weave.client.subscription.SubscriptionRefreshReviewIntegrationTest,io.weave.client.subscription.SubscriptionStoreIntegrationTest,io.weave.client.data.CrossProcessSettingsIntegrationTest,io.weave.client.routing.RemoteRuleSetStoreIntegrationTest,io.weave.client.subscription.SubscriptionTransferIntegrationTest,io.weave.client.transfer.BackupRestoreIntegrationTest,io.weave.client.data.ProfileRecoveryIntegrationTest,io.weave.client.ui.StartupRecoveryIntegrationTest
"${target[@]}" shell am instrument -w -r -e weaveIsolatedEmulator 1 -e class "$classes" \
  io.weave.client.test/androidx.test.runner.AndroidJUnitRunner > "$results/instrumentation.txt" 2>&1
if grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|INSTRUMENTATION_ABORTED' "$results/instrumentation.txt" ||
   ! grep -Eq '^OK \([1-9][0-9]* tests?\)' "$results/instrumentation.txt"; then
  cat "$results/instrumentation.txt"
  exit 1
fi
grep -E '^Time:|^OK \(' "$results/instrumentation.txt"
