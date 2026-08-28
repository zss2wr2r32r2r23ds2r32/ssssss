#!/bin/sh
set -eu
ROOT="$(CDPATH= cd -- "$(dirname "$0")/.." && pwd)"
OUT="$ROOT/dist/PinePollenMacro.exe"
mkdir -p "$ROOT/dist" "$ROOT/build"
CC="${CC:-x86_64-w64-mingw32-gcc}"
WINDRES="${WINDRES:-x86_64-w64-mingw32-windres}"

"$WINDRES" "$ROOT/launcher/PinePollenLauncher.rc" -O coff -o "$ROOT/build/PinePollenLauncher.res"
"$CC" -O2 -s -mwindows \
  "$ROOT/launcher/PinePollenLauncher.c" \
  "$ROOT/build/PinePollenLauncher.res" \
  -o "$OUT" \
  -ladvapi32 -lcrypt32 -lshell32 -luser32 -lgdi32 -lcomctl32

echo "built $OUT"
file "$OUT"
