#!/usr/bin/env python3
import importlib.util
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.dont_write_bytecode = True
spec = importlib.util.spec_from_file_location("code_verification", Path(__file__).with_name("verify-android-test-code.py"))
verification = importlib.util.module_from_spec(spec)
spec.loader.exec_module(verification)


class CodeVerificationTests(unittest.TestCase):
    def test_both_packages_must_report_success(self):
        def run(args, **kwargs):
            self.assertEqual(["adb", "-P", "5049", "-s", "emulator-5598", "shell", "cmd", "package", "compile", "-m", "verify", "-f"], args[:-1])
            self.assertEqual(300, kwargs["timeout"])
            kwargs["stdout"].write("Success\n")
            return subprocess.CompletedProcess(args, 0)
        with tempfile.TemporaryDirectory() as directory, patch.dict(os.environ, WEAVE_ISOLATED_EMULATOR="1"):
            with patch.object(verification.subprocess, "run", side_effect=run) as invoked:
                self.assertTrue(verification.verify("adb", 5049, "emulator-5598", Path(directory)))
                self.assertEqual(["io.weave.client", "io.weave.client.test"], [call.args[0][-1] for call in invoked.call_args_list])

    def test_failure_or_timeout_stops_before_next_package(self):
        for mode in ("no-success", "nonzero", "timeout"):
            with self.subTest(mode=mode), tempfile.TemporaryDirectory() as directory, patch.dict(os.environ, WEAVE_ISOLATED_EMULATOR="1"):
                def run(args, **kwargs):
                    kwargs["stdout"].write("Failure\n" if mode == "no-success" else "Success\n")
                    if mode == "timeout": raise subprocess.TimeoutExpired(args, 300)
                    return subprocess.CompletedProcess(args, 1 if mode == "nonzero" else 0)
                with patch.object(verification.subprocess, "run", side_effect=run) as invoked:
                    self.assertFalse(verification.verify("adb", 5049, "emulator-5598", Path(directory)))
                    self.assertEqual(1, invoked.call_count)
                    self.assertFalse(json.loads((Path(directory) / "code-verification.json").read_text())["io.weave.client"]["verified"])

    def test_physical_device_and_missing_opt_in_are_rejected(self):
        with patch.object(verification.subprocess, "run") as invoked:
            for enabled, serial in (("1", "phone-123"), ("0", "emulator-5598")):
                with patch.dict(os.environ, WEAVE_ISOLATED_EMULATOR=enabled), self.assertRaises(ValueError):
                    verification.verify("adb", 5049, serial, Path("unused"))
            invoked.assert_not_called()


if __name__ == "__main__":
    unittest.main()
