#!/usr/bin/env python3
"""Wait for services, not just the boot flag, on an explicitly isolated emulator."""
import os
from pathlib import Path
import re
import subprocess
import sys
import time


def wait_ready(adb, serial, port, timeout, emulator_pid=None):
    deadline = time.monotonic() + timeout
    stable = 0
    stable_pid = None
    last_state = None
    while time.monotonic() < deadline:
        if emulator_pid is not None:
            try:
                os.kill(emulator_pid, 0)
            except ProcessLookupError:
                raise RuntimeError("The test emulator exited before its services became ready")
        values = []
        for command in (("getprop", "ro.kernel.qemu"), ("getprop", "sys.boot_completed"),
                        ("pidof", "system_server"), ("cmd", "package", "path", "android"),
                        ("am", "get-current-user"), ("dumpsys", "activity", "-a", "processes"),
                        ("pidof", "system_server")):
            try:
                result = subprocess.run([adb, "-P", str(port), "-s", serial, "shell", *command],
                                        capture_output=True, text=True, timeout=5, check=False)
                values.append(result.stdout.strip() if result.returncode == 0 else "")
            except subprocess.TimeoutExpired:
                values.append("")
        # Binder services can be published before SystemServer finishes startup.
        # sys.boot_completed can also remain set across a system_server restart.
        flags = dict(re.findall(r"\b(mProcessesReady|mSystemReady|mBooted|mBooting)=(true|false)\b", values[5]))
        booted = flags == dict(mProcessesReady="true", mSystemReady="true", mBooted="true", mBooting="false")
        pid = values[2] if values[2].isdigit() and int(values[2]) > 0 and values[2] == values[6] else None
        state = (values[0] == "1", values[1] == "1", values[3].startswith("package:"), values[4].isdigit(), booted, pid)
        if state != last_state:
            print("Emulator readiness (emulated, boot, package, activity, system-ready, stable-pid):", state, flush=True)
            last_state = state
        stable = (stable + 1 if pid == stable_pid else 1) if all(state) else 0
        stable_pid = pid if all(state) else None
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
