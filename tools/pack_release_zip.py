#!/usr/bin/env python3
"""Pack a Natro-style zip users can extract on Windows."""

from __future__ import annotations

import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "dist" / "PinePollenMacro.zip"
FOLDER = "PinePollenMacro"

FILES = [
    "HOW_TO_RUN.txt",
    "START.bat",
    "pine_macro.ahk",
    "licenses.json",
    "LICENSE.md",
    "README.md",
]
DIRS = ["lib", "paths", "patterns", "settings"]


def pack(out: Path = OUT) -> Path:
    out.parent.mkdir(parents=True, exist_ok=True)
    exe = ROOT / "dist" / "PinePollenMacro.exe"
    with zipfile.ZipFile(out, "w", compression=zipfile.ZIP_DEFLATED) as zf:
        for rel in FILES:
            src = ROOT / rel
            if src.is_file():
                zf.write(src, f"{FOLDER}/{rel}")
        for folder in DIRS:
            base = ROOT / folder
            for path in base.rglob("*"):
                if path.is_file() and path.name != ".gitkeep":
                    zf.write(path, f"{FOLDER}/{path.relative_to(ROOT).as_posix()}")
                elif path.is_file() and path.name == ".gitkeep":
                    zf.write(path, f"{FOLDER}/{path.relative_to(ROOT).as_posix()}")
        if exe.is_file():
            zf.write(exe, f"{FOLDER}/PinePollenMacro.exe")
    return out


if __name__ == "__main__":
    path = pack()
    print(f"wrote {path} ({path.stat().st_size} bytes)")
