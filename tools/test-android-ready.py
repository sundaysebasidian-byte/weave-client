#!/usr/bin/env python3
"""Regression coverage for slow or unstable disposable-emulator startup."""
import importlib.util
from pathlib import Path
import subprocess
import unittest
from unittest.mock import patch

spec = importlib.util.spec_from_file_location("readiness", Path(__file__).with_name("wait-android-ready.py"))
readiness = importlib.util.module_from_spec(spec)
spec.loader.exec_module(readiness)


class ReadinessTests(unittest.TestCase):
    def run_states(self, states, timeout=30, pid=None):
        elapsed = 0
        calls = []

        def sleep(seconds):
            nonlocal elapsed
            elapsed += seconds

        def run(args, **kwargs):
            round_index, command_index = divmod(len(calls), 4)
            calls.append(args)
            value = states[min(round_index, len(states) - 1)][command_index]
            if isinstance(value, Exception):
                raise value
            return subprocess.CompletedProcess(args, 0, stdout=value, stderr="")

        with patch.object(readiness.time, "monotonic", side_effect=lambda: elapsed), \
                patch.object(readiness.time, "sleep", side_effect=sleep), \
                patch.object(readiness.subprocess, "run", side_effect=run):
            readiness.wait_ready("test-adb", "emulator-5580", 5041, timeout, pid)
        self.assertTrue(all(call[:5] == ["test-adb", "-P", "5041", "-s", "emulator-5580"] for call in calls))
        return len(calls) // 4

    def test_boot_flag_does_not_replace_package_and_activity_services(self):
        ready = ("1", "1", "package:/system/framework/framework-res.apk", "0")
        self.assertEqual(4, self.run_states([("1", "1", "", ""), ("1", "1", ready[2], ""), ready, ready]))

    def test_transient_failure_resets_stable_samples(self):
        ready = ("1", "1", "package:/system/framework/framework-res.apk", "0")
        timeout = subprocess.TimeoutExpired("test-adb", 5)
        self.assertEqual(4, self.run_states([ready, ("1", "1", timeout, "0"), ready, ready]))

    def test_missing_services_fail_with_bounded_wait(self):
        with self.assertRaisesRegex(RuntimeError, "Timed out"):
            self.run_states([("1", "1", "", "")], timeout=6)

    def test_exited_emulator_fails_immediately(self):
        with patch.object(readiness.os, "kill", side_effect=ProcessLookupError), \
                self.assertRaisesRegex(RuntimeError, "emulator exited"):
            self.run_states([], pid=123)


if __name__ == "__main__":
    unittest.main()
