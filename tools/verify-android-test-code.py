#!/usr/bin/env python3
"""Prepare verification artifacts before instrumentation on an opted-in emulator."""
import json
import os
from pathlib import Path
import subprocess
import sys


def verify(adb, port, serial, results):
    if os.environ.get("WEAVE_ISOLATED_EMULATOR") != "1" or not serial.startswith("emulator-"):
        raise ValueError("Code verification requires an explicitly disposable emulator")
    results.mkdir(parents=True, exist_ok=True)
    status = {}
    for package in ("io.weave.client", "io.weave.client.test"):
        path = results / (package + "-verification.txt")
        args = [adb, "-P", str(port), "-s", serial, "shell", "cmd", "package", "compile", "-m", "verify", "-f", package]
        with path.open("w") as log:
            try:
                result = subprocess.run(args, stdout=log, stderr=subprocess.STDOUT, text=True, timeout=300)
                status[package] = {"exit_code": result.returncode}
            except subprocess.TimeoutExpired:
                status[package] = {"error": "timeout"}
        success = status[package].get("exit_code") == 0 and "Success" in path.read_text().splitlines()
        status[package]["verified"] = success
        (results / "code-verification.json").write_text(json.dumps(status, indent=2) + "\n")
        if not success:
            return False
    return True


if __name__ == "__main__":
    adb, port, serial, results = sys.argv[1:]
    if not verify(adb, int(port), serial, Path(results)):
        sys.exit("APK code verification failed; instrumentation was not started")
