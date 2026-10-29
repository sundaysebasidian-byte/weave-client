#!/usr/bin/env python3
"""Bounded failure evidence from the disposable emulator used by storage tests."""
import json
import os
from pathlib import Path
import subprocess
import sys


def collect(adb, port, serial, results):
    results.mkdir(parents=True, exist_ok=True)
    outcomes = {}
    commands = {
        "last-anr.txt": ["dumpsys", "activity", "lastanr"],
        "app-anr-dropbox.txt": ["dumpsys", "dropbox", "--print", "data_app_anr"],
        "data-space.txt": ["df", "-h", "/data"],
    }
    for name, command in commands.items():
        try:
            with (results / name).open("w") as output:
                result = subprocess.run([adb, "-P", str(port), "-s", serial, "shell", *command],
                                        stdout=output, stderr=subprocess.STDOUT, timeout=10, check=False)
                outcomes[name] = {"exit_code": result.returncode}
        except subprocess.TimeoutExpired:
            outcomes[name] = {"error": "timeout", "partial_output_retained": True}
        except OSError as error:
            outcomes[name] = {"error": type(error).__name__}
    (results / "failure-collection.json").write_text(json.dumps(outcomes, indent=2))


if __name__ == "__main__":
    if len(sys.argv) != 5 or os.environ.get("WEAVE_ISOLATED_EMULATOR") != "1" or not sys.argv[3].startswith("emulator-"):
        raise SystemExit("Failure collection requires an explicitly isolated emulator")
    collect(sys.argv[1], int(sys.argv[2]), sys.argv[3], Path(sys.argv[4]))
