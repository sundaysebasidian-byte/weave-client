#!/usr/bin/env python3
import importlib.util
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

sys.dont_write_bytecode = True
spec = importlib.util.spec_from_file_location("failure", Path(__file__).with_name("collect-android-failure.py"))
failure = importlib.util.module_from_spec(spec)
spec.loader.exec_module(failure)


class FailureEvidenceTests(unittest.TestCase):
    def test_partial_timeout_and_permission_failure_do_not_lose_other_evidence(self):
        calls = []

        def run(args, **kwargs):
            calls.append(args)
            self.assertEqual(10, kwargs["timeout"])
            self.assertEqual(["fake-adb", "-P", "5049", "-s", "emulator-5598", "shell"], args[:6])
            if len(calls) == 1:
                kwargs["stdout"].write("partial ANR stack\n")
                raise subprocess.TimeoutExpired(args, 10)
            if len(calls) == 2:
                kwargs["stdout"].write("permission denied\n")
                return subprocess.CompletedProcess(args, 1)
            kwargs["stdout"].write("synthetic capacity\n")
            return subprocess.CompletedProcess(args, 0)

        with tempfile.TemporaryDirectory(prefix="weave-failure-evidence-") as directory:
            results = Path(directory)
            with patch.object(failure.subprocess, "run", side_effect=run):
                failure.collect("fake-adb", 5049, "emulator-5598", results)
            status = json.loads((results / "failure-collection.json").read_text())
            self.assertEqual("timeout", status["last-anr.txt"]["error"])
            self.assertEqual("partial ANR stack\n", (results / "last-anr.txt").read_text())
            self.assertEqual(1, status["app-anr-dropbox.txt"]["exit_code"])
            self.assertEqual(0, status["data-space.txt"]["exit_code"])
            self.assertEqual("synthetic capacity\n", (results / "data-space.txt").read_text())


if __name__ == "__main__":
    unittest.main()
