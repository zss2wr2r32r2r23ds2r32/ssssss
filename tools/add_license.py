#!/usr/bin/env python3
"""Add a hashed license key to licenses.json."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

from pine_core.license_keys import LicenseError, LicenseStore  # noqa: E402


def main() -> int:
    parser = argparse.ArgumentParser(description="Register a Pine Pollen Macro license key")
    parser.add_argument("--key", required=True, help="Plaintext license key to hash and store")
    parser.add_argument("--label", default="", help="Optional label")
    parser.add_argument("--role", default="user", help="Role stored with the key (user/admin)")
    parser.add_argument("--note", default="", help="Optional note")
    parser.add_argument(
        "--licenses",
        default=str(ROOT / "licenses.json"),
        help="Path to licenses.json",
    )
    args = parser.parse_args()
    store = LicenseStore(Path(args.licenses))
    try:
        record = store.add_key(args.key, label=args.label, role=args.role, note=args.note)
    except LicenseError as exc:
        print(f"error: {exc}", file=sys.stderr)
        return 1
    print(f"added hash={record.hash} role={record.role} label={record.label!r}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
