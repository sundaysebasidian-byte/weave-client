"""Exercise the public-fixture exception without changing the source checkout."""
from pathlib import Path
import shutil
import subprocess
import tempfile
import unittest

REPO = Path(__file__).resolve().parents[1]


class FixtureCredentialGuardTests(unittest.TestCase):
    def setUp(self):
        self.temporary = tempfile.TemporaryDirectory(prefix="weave-audit-test-")
        self.addCleanup(self.temporary.cleanup)
        self.root = Path(self.temporary.name)
        inputs = ["tools/audit-local-release.sh", "app/src/main/AndroidManifest.xml", "app/build.gradle.kts",
                  "macos/Package.swift", "ios/WeaveCore/Package.swift", "PRIVACY.md", "SECURITY.md",
                  "THIRD_PARTY.md", "NOTICE", "docs/LOCAL_ONLY_RELEASE_PROFILE.md", "docs/NETWORK_ENDPOINT_INVENTORY.md",
                  "tests/fixtures/https/test-only-key.pem"]
        inputs += [str(p.relative_to(REPO)) for p in (REPO / "tests/fixtures/provider-materials").glob("*.pem")]
        for relative in inputs:
            target = self.root / relative
            target.parent.mkdir(parents=True, exist_ok=True)
            shutil.copyfile(REPO / relative, target)
        for directory in ["ios/WeaveIOS.xcodeproj", "windows/src"]:
            (self.root / directory).mkdir(parents=True, exist_ok=True)

    def audit(self):
        return subprocess.run(["bash", "tools/audit-local-release.sh"], cwd=self.root,
                              text=True, capture_output=True, timeout=20)

    def test_exact_public_identities_pass(self):
        result = self.audit()
        self.assertEqual(result.returncode, 0, result.stdout + result.stderr)

    def test_changed_known_identity_fails(self):
        path = self.root / "tests/fixtures/provider-materials/rsa-pkcs8.pem"
        path.write_bytes(path.read_bytes() + b"\n")
        result = self.audit()
        self.assertNotEqual(result.returncode, 0)
        self.assertIn("public TLS fixture key is missing or changed", result.stderr)
        self.assertIn("possible private key or service credential found", result.stderr)

    def test_unknown_key_is_not_exempt(self):
        target = self.root / "tests/fixtures/provider-materials/unreviewed.pem"
        for name in ["rsa-pkcs1.pem", "ecdsa-sec1.pem", "ed25519-pkcs8.pem"]:
            with self.subTest(name=name):
                shutil.copyfile(REPO / "tests/fixtures/provider-materials" / name, target)
                result = self.audit()
                self.assertNotEqual(result.returncode, 0)
                self.assertIn("possible private key or service credential found", result.stderr)


if __name__ == "__main__":
    unittest.main()
