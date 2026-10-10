#!/usr/bin/env python3
"""Check a license key against licenses.json."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

from pine_core.license_keys import LicenseError, LicenseStore, TESTING_LICENSE_KEY  # noqa: E402


def main() -> int:
    parser = argparse.ArgumentParser(description="Validate a Pine Pollen Macro license key")
    parser.add_argument("key", nargs="?", default=TESTING_LICENSE_KEY)
    parser.add_argument("--licenses", default=str(ROOT / "licenses.json"))
    args = parser.parse_args()
    store = LicenseStore(Path(args.licenses))
    try:
        record = store.validate(args.key)
    except LicenseError as exc:
        print(f"INVALID: {exc}")
        return 1
    print(f"VALID  role={record.role}  label={record.label}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
