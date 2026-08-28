from __future__ import annotations

import json
import tempfile
import unittest
from pathlib import Path

from pine_core.license_keys import (
    DEFAULT_SALT,
    TESTING_LICENSE_KEY,
    LicenseError,
    LicenseStore,
    hash_license_key,
    normalize_key,
)

ROOT = Path(__file__).resolve().parent.parent
LICENSES = ROOT / "licenses.json"


class LicenseTests(unittest.TestCase):
    def setUp(self) -> None:
        self.store = LicenseStore(LICENSES)

    def test_testing_key_constant(self) -> None:
        self.assertEqual(TESTING_LICENSE_KEY, "admintest123")

    def test_admintest123_is_valid(self) -> None:
        record = self.store.validate("admintest123")
        self.assertEqual(record.role, "admin")
        self.assertIn("testing", record.label.lower() + record.note.lower())

    def test_admintest123_hash_matches_licenses_json(self) -> None:
        data = json.loads(LICENSES.read_text(encoding="utf-8"))
        expected = hash_license_key("admintest123", data["salt"])
        hashes = [item["hash"] for item in data["keys"]]
        self.assertIn(expected, hashes)
        self.assertEqual(expected, "ae89aa89b4126018c97e89cb8fba7b3dd9bcae0fd57e8c2ff79f4558b00c36a4")

    def test_salt_is_stable(self) -> None:
        self.assertEqual(self.store.salt, DEFAULT_SALT)

    def test_empty_key_rejected(self) -> None:
        with self.assertRaises(LicenseError):
            self.store.validate("   ")
        self.assertFalse(self.store.is_valid(""))

    def test_wrong_key_rejected(self) -> None:
        self.assertFalse(self.store.is_valid("admintest122"))
        self.assertFalse(self.store.is_valid("ADMINTEST123"))
        self.assertFalse(self.store.is_valid("wrong-key"))

    def test_surrounding_whitespace_is_stripped(self) -> None:
        record = self.store.validate("  admintest123  ")
        self.assertEqual(record.role, "admin")

    def test_normalize_none_is_empty(self) -> None:
        self.assertEqual(normalize_key(None), "")  # type: ignore[arg-type]

    def test_add_and_lookup_new_key(self) -> None:
        with tempfile.TemporaryDirectory() as tmp:
            path = Path(tmp) / "licenses.json"
            path.write_text(LICENSES.read_text(encoding="utf-8"), encoding="utf-8")
            store = LicenseStore(path)
            record = store.add_key("friend-key-9", label="Friend", role="user")
            self.assertTrue(store.is_valid("friend-key-9"))
            self.assertEqual(store.lookup("friend-key-9").label, "Friend")
            self.assertEqual(record.hash, hash_license_key("friend-key-9", store.salt))
            with self.assertRaises(LicenseError):
                store.add_key("friend-key-9")

    def test_ahk_license_file_uses_same_salt_and_test_key(self) -> None:
        ahk = (ROOT / "lib" / "License.ahk").read_text(encoding="utf-8")
        self.assertIn('Salt := "pine-pollen-macro-v1"', ahk)
        gui = (ROOT / "pine_macro.ahk").read_text(encoding="utf-8")
        self.assertIn("admintest123", gui)
        prompt = (ROOT / "lib" / "License.ahk").read_text(encoding="utf-8")
        self.assertIn("admintest123", prompt)


if __name__ == "__main__":
    unittest.main()
