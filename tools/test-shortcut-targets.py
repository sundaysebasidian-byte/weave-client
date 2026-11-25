#!/usr/bin/env python3
"""Check static launcher shortcut identity; does not launch any shortcut, device or VPN."""
import re
import unittest
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
A = "{http://schemas.android.com/apk/res/android}"

class ShortcutTargets(unittest.TestCase):
    def test_production_shortcuts_target_this_installation(self):
        build = (ROOT / "app/build.gradle.kts").read_text()
        package = re.search(r'^\s*applicationId = "([^"]+)"', build, re.M)[1]
        namespace = re.search(r'^\s*namespace = "([^"]+)"', build, re.M)[1]
        m = ET.parse(ROOT / "app/src/main/AndroidManifest.xml")
        host = next(activity for activity in m.findall(".//activity")
                    if any(meta.get(A + "name") == "android.app.shortcuts" for meta in activity.findall("meta-data")))
        host_class = host.get(A + "name")
        if host_class.startswith("."):
            host_class = namespace + host_class
        shortcuts = ET.parse(ROOT / "app/src/main/res/xml/shortcuts.xml").findall("shortcut")
        self.assertEqual({"connect", "disconnect", "import"}, {s.get(A + "shortcutId") for s in shortcuts})
        for s in shortcuts:
            with self.subTest(shortcut=s.get(A + "shortcutId")):
                intent = s.find("intent")
                self.assertEqual((package, host_class), (intent.get(A + "targetPackage"), intent.get(A + "targetClass")))

    def test_isolated_fixture_does_not_launch_product_shortcuts(self):
        self.assertEqual([], ET.parse(ROOT / "app/src/deviceTest/res/xml/shortcuts.xml").findall("shortcut"))

if __name__ == "__main__":
    unittest.main()
