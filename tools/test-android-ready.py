#!/usr/bin/env python3
"""Regression coverage for slow or unstable disposable-emulator startup."""
import importlib.util
from pathlib import Path
import subprocess
import sys
import unittest
from unittest.mock import patch

sys.dont_write_bytecode = True
spec = importlib.util.spec_from_file_location("readiness", Path(__file__).with_name("wait-android-ready.py"))
readiness = importlib.util.module_from_spec(spec)
spec.loader.exec_module(readiness)


class ReadinessTests(unittest.TestCase):
    ready = ("1", "1", "2248", "package:/system/framework/framework-res.apk", "0",
             "mProcessesReady=true mSystemReady=true mBooted=true mBooting=false", "2248")

    def run_states(self, states, timeout=30, pid=None):
        elapsed = 0
        calls = []

        def sleep(seconds):
            nonlocal elapsed
            elapsed += seconds

        def run(args, **kwargs):
            round_index, command_index = divmod(len(calls), 7)
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
        return len(calls) // 7

    def test_boot_flag_does_not_replace_package_and_activity_services(self):
        ready = self.ready
        self.assertEqual(4, self.run_states([(*ready[:3], "", "", *ready[5:]),
                                             (*ready[:4], "", *ready[5:]), ready, ready]))

    def test_transient_failure_resets_stable_samples(self):
        ready = self.ready
        timeout = subprocess.TimeoutExpired("test-adb", 5)
        self.assertEqual(4, self.run_states([ready, (*ready[:3], timeout, *ready[4:]), ready, ready]))

    def test_restarted_system_server_resets_stable_samples(self):
        ready = self.ready
        restarted = (*ready[:2], "3348", *ready[3:6], "3348")
        self.assertEqual(3, self.run_states([ready, restarted, restarted]))

    def test_restart_during_sample_and_incomplete_dump_cannot_confirm_readiness(self):
        ready = self.ready
        self.assertEqual(5, self.run_states([(*ready[:6], "3348"),
            (*ready[:5], "mBooted=true", ready[6]), (*ready[:5], "", ready[6]), ready, ready]))

    def test_missing_services_fail_with_bounded_wait(self):
        with self.assertRaisesRegex(RuntimeError, "Timed out"):
            self.run_states([("1", "1", "", "", "", "", "")], timeout=6)

    def test_exited_emulator_fails_immediately(self):
        with patch.object(readiness.os, "kill", side_effect=ProcessLookupError), \
                self.assertRaisesRegex(RuntimeError, "emulator exited"):
            self.run_states([], pid=123)

    def test_published_services_during_system_server_startup_are_not_ready(self):
        elapsed = 0

        def sleep(seconds):
            nonlocal elapsed
            elapsed += seconds

        def run(args, **kwargs):
            command = tuple(args[6:])
            values = {
                ("getprop", "ro.kernel.qemu"): "1",
                ("getprop", "sys.boot_completed"): "1",
                ("cmd", "package", "path", "android"): "package:/system/framework/framework-res.apk",
                ("am", "get-current-user"): "0",
                ("pidof", "system_server"): "2248",
                ("dumpsys", "activity", "-a", "processes"):
                    "mProcessesReady=false mSystemReady=false mBooted=false mBooting=true",
            }
            return subprocess.CompletedProcess(args, 0, stdout=values[command], stderr="")

        with patch.object(readiness.time, "monotonic", side_effect=lambda: elapsed), \
                patch.object(readiness.time, "sleep", side_effect=sleep), \
                patch.object(readiness.subprocess, "run", side_effect=run), \
                self.assertRaisesRegex(RuntimeError, "Timed out"):
            readiness.wait_ready("test-adb", "emulator-5580", 5041, 6)


if __name__ == "__main__":
    unittest.main()
