"""Parse GUI/config numbers the same way the AutoHotkey script does.

AHK v2.1's Number()/Integer() are type checks, not converters, so the
macro uses ``value + 0`` after IsNumber(). This helper mirrors that.
"""

from __future__ import annotations

import math
from typing import Any


def parse_number(text: Any, fallback: float) -> float:
    raw = "" if text is None else str(text)
    raw = raw.strip().replace(",", "")
    if raw == "":
        return float(fallback)
    try:
        value = float(raw)
    except ValueError:
        return float(fallback)
    if math.isnan(value) or math.isinf(value):
        return float(fallback)
    return value


def parse_integer(text: Any, fallback: int, minimum: int | None = None, maximum: int | None = None) -> int:
    value = int(round(parse_number(text, fallback)))
    if minimum is not None:
        value = max(minimum, value)
    if maximum is not None:
        value = min(maximum, value)
    return value
