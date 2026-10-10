"""License-key validation for Pine Pollen Macro.

Keys are stored as SHA-256 hashes of ``{salt}:{normalized_key}``.
The bundled testing key is ``admintest123``.
"""

from __future__ import annotations

import hashlib
import json
from dataclasses import dataclass
from pathlib import Path
from typing import Any

DEFAULT_SALT = "pine-pollen-macro-v1"
TESTING_LICENSE_KEY = "admintest123"
REPO_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_LICENSES_PATH = REPO_ROOT / "licenses.json"


class LicenseError(ValueError):
    """Raised when a license key is missing or not in the allowlist."""


def normalize_key(key: str) -> str:
    """Strip surrounding whitespace. Keys are case-sensitive."""
    if key is None:
        return ""
    return str(key).strip()


def hash_license_key(key: str, salt: str = DEFAULT_SALT) -> str:
    normalized = normalize_key(key)
    payload = f"{salt}:{normalized}".encode("utf-8")
    return hashlib.sha256(payload).hexdigest()


@dataclass(frozen=True)
class LicenseRecord:
    hash: str
    label: str = ""
    role: str = "user"
    note: str = ""

    @classmethod
    def from_dict(cls, data: dict[str, Any]) -> "LicenseRecord":
        digest = str(data.get("hash", "")).lower()
        if len(digest) != 64 or any(c not in "0123456789abcdef" for c in digest):
            raise LicenseError("licenses.json contains an invalid key hash")
        return cls(
            hash=digest,
            label=str(data.get("label", "")),
            role=str(data.get("role", "user")),
            note=str(data.get("note", "")),
        )


class LicenseStore:
    def __init__(self, licenses_path: Path | None = None, salt: str | None = None):
        self.licenses_path = Path(licenses_path) if licenses_path else DEFAULT_LICENSES_PATH
        self._salt = salt
        self._records: list[LicenseRecord] | None = None

    def load(self) -> dict[str, Any]:
        if not self.licenses_path.is_file():
            raise LicenseError(f"License file not found: {self.licenses_path}")
        try:
            data = json.loads(self.licenses_path.read_text(encoding="utf-8"))
        except json.JSONDecodeError as exc:
            raise LicenseError(f"License file is not valid JSON: {exc}") from exc
        if not isinstance(data, dict):
            raise LicenseError("License file must be a JSON object")
        return data

    @property
    def salt(self) -> str:
        if self._salt is not None:
            return self._salt
        data = self.load()
        return str(data.get("salt") or DEFAULT_SALT)

    @property
    def records(self) -> list[LicenseRecord]:
        if self._records is None:
            data = self.load()
            keys = data.get("keys")
            if not isinstance(keys, list) or not keys:
                raise LicenseError("License file has no keys")
            self._records = [LicenseRecord.from_dict(item) for item in keys]
        return self._records

    def lookup(self, key: str) -> LicenseRecord:
        normalized = normalize_key(key)
        if not normalized:
            raise LicenseError("Enter a license key")
        digest = hash_license_key(normalized, self.salt)
        for record in self.records:
            if record.hash == digest:
                return record
        raise LicenseError("Invalid license key")

    def validate(self, key: str) -> LicenseRecord:
        return self.lookup(key)

    def is_valid(self, key: str) -> bool:
        try:
            self.lookup(key)
            return True
        except LicenseError:
            return False

    def add_key(self, key: str, label: str = "", role: str = "user", note: str = "") -> LicenseRecord:
        normalized = normalize_key(key)
        if not normalized:
            raise LicenseError("Cannot add an empty license key")
        data = self.load()
        salt = str(data.get("salt") or DEFAULT_SALT)
        digest = hash_license_key(normalized, salt)
        keys = list(data.get("keys") or [])
        for item in keys:
            if str(item.get("hash", "")).lower() == digest:
                raise LicenseError("That license key is already registered")
        record = LicenseRecord(hash=digest, label=label, role=role, note=note)
        keys.append(
            {
                "hash": record.hash,
                "label": record.label,
                "role": record.role,
                "note": record.note,
            }
        )
        data["salt"] = salt
        data["keys"] = keys
        self.licenses_path.write_text(json.dumps(data, indent=2) + "\n", encoding="utf-8")
        self._records = None
        self._salt = salt
        return record
