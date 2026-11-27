#!/usr/bin/env python3
"""Negative tests for the staged Compose catalog gate."""

import importlib.util
import json
from pathlib import Path
from tempfile import TemporaryDirectory
import unittest

spec = importlib.util.spec_from_file_location("compose_locales", Path(__file__).with_name("check-android-compose-locales.py"))
checker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checker)


class ComposeLocaleGateTest(unittest.TestCase):
    def setUp(self):
        self.tmp = TemporaryDirectory()
        self.addCleanup(self.tmp.cleanup)
        self.root = Path(self.tmp.name)
        self.write("source-static", {"id": "连接 VPN", "en": "Connect VPN"})
        self.write("source-rc6-delta")
        self.write("source-dynamic", {"id": "dynamic:count", "en": "Connected to {name}"})
        self.write("es", {"id": "连接 VPN", "text": "Conectar la VPN"}, {"id": "dynamic:count", "text": "Conectado a {name}"})
        self.write("ko", {"id": "连接 VPN", "text": "VPN 연결"}, {"id": "dynamic:count", "text": "{name}에 연결됨"})
        self.write("ar", {"id": "连接 VPN", "text": "الاتصال عبر VPN"}, {"id": "dynamic:count", "text": "متصل بـ \u2068{name}\u2069"})
        self.write("ru", {"id": "连接 VPN", "text": "Подключить VPN"}, {"id": "dynamic:count", "text": "Подключено к {name}"})
        self.write("hi", {"id": "连接 VPN", "text": "VPN कनेक्ट करें"}, {"id": "dynamic:count", "text": "{name} से कनेक्ट है"})

    def write(self, typ, *rows):
        (self.root / (typ + ".jsonl")).write_text("".join(json.dumps(row) + "\n" for row in rows), encoding="utf-8")

    def test_complete_valid_catalogs(self):
        self.assertEqual(checker.run(self.root, release=True), ([], dict.fromkeys(checker.SCRIPT, (1, 1))))

    def test_missing_key_blocks_release_only(self):
        self.write("ar", {"id": "连接 VPN", "text": "الاتصال عبر VPN"})
        self.assertEqual(checker.run(self.root)[0], [])
        self.assertEqual(checker.run(self.root, release=True)[0], ["ar: 1 missing release keys"])

    def test_missing_placeholder_and_unbalanced_isolate_are_rejected(self):
        self.write("ar", {"id": "dynamic:count", "text": "متصل \u2068"})
        errors, _ = checker.run(self.root)
        self.assertTrue(any("placeholders mismatch" in error for error in errors))
        self.assertTrue(any("unbalanced or unsafe bidi" in error for error in errors))

    def test_literals_do_not_fall_back_or_drop_protocol(self):
        self.write("es", {"id": "连接 VPN", "text": "Connect VPN"}, {"id": "连接 VPN", "text": "Conectar"})
        errors, _ = checker.run(self.root)
        for expected in ["copied English", "duplicate", "missing protected tokens VPN"]:
            self.assertTrue(any(expected in error for error in errors), errors)


if __name__ == "__main__":
    unittest.main()
