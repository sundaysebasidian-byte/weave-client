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
# Preserve native/runner crash diagnostics too: am instrument can exit successfully while
# reporting only "Process crashed". Start before readiness to retain platform startup failures.
# This process belongs exclusively to this test invocation.
"${target[@]}" logcat -v threadtime -T 1 > "$results/logcat.txt" 2>&1 &
logcat_pid=$!
cleanup() {
  result=$?
  if [[ $result != 0 ]]; then
    python3 "$root/tools/collect-android-failure.py" "$adb" "$port" "$serial" "$results" ||
      echo 'Failure diagnostics were incomplete; original test failure is retained.' >&2
  fi
  kill "$logcat_pid" 2>/dev/null || true
  wait "$logcat_pid" 2>/dev/null || true
  return "$result"
}
trap cleanup EXIT
python3 "$root/tools/wait-android-ready.py" "$serial" | tee "$results/readiness.txt"
abi=$("${target[@]}" shell getprop ro.product.cpu.abi | tr -d '\r')
case "$abi" in arm64-v8a|armeabi-v7a|x86|x86_64) ;; *) echo 'Unsupported emulator ABI' >&2; exit 1 ;; esac
product_apk="$root/app/build/outputs/apk/debug/app-$abi-debug.apk"
test_apk="$root/app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk"
# Resolve installed identities from the actual paired artifacts, not the source namespace.
aapt2="$ANDROID_HOME/build-tools/36.0.0/aapt2"
[[ -x $aapt2 ]] || { echo 'Android build-tools aapt2 is required.' >&2; exit 1; }
product_id=$("$aapt2" dump packagename "$product_apk")
test_id=$("$aapt2" dump packagename "$test_apk")
[[ $product_id =~ ^io\.weave\.client(\.next)?$ && $test_id == "$product_id.test" ]] ||
  { echo 'Unexpected debug/test APK identities.' >&2; exit 1; }
component="$test_id/androidx.test.runner.AndroidJUnitRunner"
"${target[@]}" install -r "$product_apk"
"${target[@]}" install -r "$test_apk"
"${target[@]}" shell pm list instrumentation "$product_id" | tr -d '\r' |
  grep -Fx "instrumentation:$component (target=$product_id)" > \
  "$results/instrumentation-registration.txt"
# ART verification of cold APKs can exhaust the process-start deadline under software
# emulation. Do the same verification first, without disabling checks or clearing data.
python3 "$root/tools/verify-android-test-code.py" "$adb" "$port" "$serial" "$results" "$product_id" "$test_id"
classes=io.weave.client.subscription.SubscriptionRefreshReviewIntegrationTest,io.weave.client.subscription.SubscriptionStoreIntegrationTest,io.weave.client.data.CrossProcessSettingsIntegrationTest,io.weave.client.routing.RemoteRuleSetStoreIntegrationTest,io.weave.client.subscription.SubscriptionTransferIntegrationTest,io.weave.client.transfer.BackupRestoreIntegrationTest,io.weave.client.data.ProfileRecoveryIntegrationTest,io.weave.client.ui.StartupRecoveryIntegrationTest
instrument_args=(-e weaveIsolatedEmulator 1 -e class "$classes")
if [[ ${WEAVE_HOSTED_SLOW_TIMING:-} == 1 ]]; then
  instrument_args+=(-e weaveHostedSlowTiming 1)
fi
"${target[@]}" shell am instrument -w -r "${instrument_args[@]}" \
  "$component" 2>&1 | tee "$results/instrumentation.txt" | awk '
    /^INSTRUMENTATION_STATUS: (class|current|test)=[A-Za-z0-9_.$]+\r?$/ ||
    /^INSTRUMENTATION_STATUS_CODE: -?[0-9]+\r?$/ { print; fflush() }
  '
if grep -Eq 'FAILURES!!!|INSTRUMENTATION_FAILED|INSTRUMENTATION_ABORTED' "$results/instrumentation.txt" ||
   ! grep -Eq '^OK \([1-9][0-9]* tests?\)' "$results/instrumentation.txt"; then
  echo "Storage instrumentation failed; see the preserved instrumentation receipt." >&2
  exit 1
fi
grep -E '^Time:|^OK \(' "$results/instrumentation.txt"

# Run these separately so product flows and the storage/recovery tests each retain a receipt.
# The stock Intel emulator can return no active accessibility window. Exercise the visible
# chooser control here; the focused physical Back case is a separate opt-in device test.
routing_classes=io.weave.client.ui.PrimaryFlowRegressionTest
routing_classes+=,io.weave.client.ui.MainActivityRoutingJourneyTest#addingAnApplicationWaitsForAnExplicitRoutingPolicy
routing_classes+=,io.weave.client.ui.MainActivityRoutingJourneyTest#notificationEntryAndDialPersistTheUserChoicesAcrossActivityRecreation
routing_classes+=,io.weave.client.ui.WeaveAdaptiveControlsTest
routing_classes+=,io.weave.client.ui.ProductFlowVisualTest#bodySwipeStepsAndDockFollowsTheFingerWithoutCommittingCancelledDrags
"${target[@]}" shell am instrument -w -r -e isolatedRoutingTest true -e class "$routing_classes" \
  "$component" > "$results/routing-instrumentation.txt" 2>&1
grep -Fx 'OK (9 tests)' "$results/routing-instrumentation.txt"
awk '
  /^INSTRUMENTATION_STATUS_CODE: 1\r?$/ { started++ }
  /^INSTRUMENTATION_STATUS_CODE: 0\r?$/ { passed++ }
  /^INSTRUMENTATION_STATUS_CODE: -[0-9]+\r?$/ { unsuccessful++ }
  END {
    printf "Routing journeys: %d started, %d passed, %d unsuccessful\n", started, passed, unsuccessful
    exit !(started == 9 && passed == 9 && unsuccessful == 0)
  }
' "$results/routing-instrumentation.txt"
