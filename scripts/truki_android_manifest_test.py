# Copyright 2026 Truki
# SPDX-License-Identifier: AGPL-3.0-only
import hashlib
import sys
import tempfile
import unittest
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))
import truki_android_manifest as tam  # noqa: E402


class ManifestTest(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.root = Path(self.tmp.name)

    def tearDown(self):
        self.tmp.cleanup()

    def test_manifest_points_at_the_arm64_asset(self):
        apk = self.root / "Truki-phone-arm64-v8a-8.32.1-truki1.apk"
        apk.write_bytes(b"apk bytes")
        m = tam.build_manifest(176801, "8.32.1-truki1", apk, "August-Roosi/truki-android", "v8.32.1-truki1")
        self.assertTrue(m["url"].endswith("/" + apk.name))
        self.assertIn("/releases/download/v8.32.1-truki1/", m["url"])
        self.assertEqual(m["sha256sum"], hashlib.sha256(b"apk bytes").hexdigest())
        self.assertEqual(m["versionCode"], 176801)
        self.assertEqual(m["versionName"], "8.32.1-truki1")

    def test_refuses_to_release_when_the_key_backup_differs(self):
        key = self.root / "k.jks"
        copy = self.root / "c.jks"
        key.write_bytes(b"key")
        with self.assertRaises(SystemExit):
            tam.check_key_backup(key, copy)
        copy.write_bytes(b"other")
        with self.assertRaises(SystemExit):
            tam.check_key_backup(key, copy)
        copy.write_bytes(b"key")
        self.assertIsNone(tam.check_key_backup(key, copy))


if __name__ == "__main__":
    unittest.main()
