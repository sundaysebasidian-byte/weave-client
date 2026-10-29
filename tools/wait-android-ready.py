#!/usr/bin/env python3
"""Wait for services, not just the boot flag, on an explicitly isolated emulator."""
import os
from pathlib import Path
import subprocess
import sys
import time


def wait_ready(adb, serial, port, timeout, emulator_pid=None):
    deadline = time.monotonic() + timeout
    stable = 0
    last_state = None
    while time.monotonic() < deadline:
        if emulator_pid is not None:
            try:
                os.kill(emulator_pid, 0)
            except ProcessLookupError:
                raise RuntimeError("The test emulator exited before its services became ready")
        values = []
        for command in (("getprop", "ro.kernel.qemu"), ("getprop", "sys.boot_completed"),
                        ("cmd", "package", "path", "android"), ("am", "get-current-user")):
            try:
                result = subprocess.run([adb, "-P", str(port), "-s", serial, "shell", *command],
                                        capture_output=True, text=True, timeout=5, check=False)
                values.append(result.stdout.strip() if result.returncode == 0 else "")
            except subprocess.TimeoutExpired:
                values.append("")
        state = (values[0] == "1", values[1] == "1", values[2].startswith("package:"), values[3].isdigit())
        if state != last_state:
            print("Emulator readiness (emulated, boot, package, activity):", state, flush=True)
            last_state = state
        stable = stable + 1 if all(state) else 0
        if stable >= 2:
            return
        time.sleep(2)
    raise RuntimeError("Timed out waiting for stable Android package and activity services")


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: wait-android-ready.py emulator-PORT")
    serial = sys.argv[1]
    if os.environ.get("WEAVE_ISOLATED_EMULATOR") != "1" or not serial.startswith("emulator-"):
        raise SystemExit("Readiness checks require explicit opt-in to an isolated emulator")
    wait_ready(str(Path(os.environ["ANDROID_HOME"]) / "platform-tools" / "adb"), serial,
               int(os.environ.get("WEAVE_TEST_ADB_PORT", "5037")),
               int(os.environ.get("WEAVE_TEST_BOOT_TIMEOUT", "180")),
               int(os.environ["WEAVE_TEST_EMULATOR_PID"]) if "WEAVE_TEST_EMULATOR_PID" in os.environ else None)
