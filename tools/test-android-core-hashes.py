"""Host-only regression for the checked-in native core lock. No Android SDK required."""
import hashlib
from pathlib import Path
import subprocess
import tempfile
import unittest

VERIFY = Path(__file__).with_name("verify-android-core-hashes.sh").resolve()
ABIS = ("arm64-v8a", "armeabi-v7a", "x86", "x86_64")


class CoreLockTest(unittest.TestCase):
    def setUp(self):
        tmp = tempfile.TemporaryDirectory()
        self.addCleanup(tmp.cleanup)
        self.root = Path(tmp.name)
        self.lock = self.root / "core-lock.properties"
        self.lines = []
        for abi in ABIS:
            core = self.core(abi)
            core.parent.mkdir(parents=True)
            core.write_bytes(abi.encode())
            self.lines.append(f"libclash.{abi}.sha256={hashlib.sha256(core.read_bytes()).hexdigest()}\n")
        self.lock.write_text("".join(self.lines))

    def core(self, abi):
        return self.root / "app/src/main/jniLibs" / abi / "libclash.so"

    def verify(self):
        return subprocess.run(["bash", VERIFY], cwd=self.root, capture_output=True).returncode

    def test_all_abis_match(self):
        self.assertEqual(self.verify(), 0)

    def test_missing_core_and_matching_entry(self):
        self.core(ABIS[-1]).unlink()
        self.lock.write_text("".join(self.lines[:-1]))
        self.assertNotEqual(self.verify(), 0)

    def test_empty_duplicate_entry(self):
        self.lock.write_text("".join(self.lines) + f"libclash.{ABIS[0]}.sha256=\n")
        self.assertNotEqual(self.verify(), 0)

    def test_malformed_digest(self):
        self.lock.write_text("".join(self.lines[1:]) + f"libclash.{ABIS[0]}.sha256=xyz\n")
        self.assertNotEqual(self.verify(), 0)

    def test_bad_digest(self):
        self.core(ABIS[1]).write_bytes(b"modified")
        self.assertNotEqual(self.verify(), 0)

    def test_duplicate_entry(self):
        self.lock.write_text("".join(self.lines + self.lines[:1]))
        self.assertNotEqual(self.verify(), 0)


if __name__ == "__main__":
    unittest.main()
