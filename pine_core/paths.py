"""Pine Tree field path data, matching NatroMacro's pine routes and walk timing.

Walk duration uses Natro's legacy formula: ``4000 / movespeed * tiles`` milliseconds.
Path stud counts come from NatroMacro ``gtf-pinetree.ahk`` / ``wf-pinetree.ahk``.
"""

from __future__ import annotations

from typing import Any

DEFAULT_MOVESPEED = 28.0
TILE_MS = 4000.0  # milliseconds to walk 1 tile at 1 movespeed

# NatroMacro default Pine Tree gather settings
PINE_DEFAULTS: dict[str, Any] = {
    "pattern": "CornerXSnake",
    "size": "M",
    "width": 3,
    "camera": "Left",
    "turns": 2,
    "sprinkler": "Upper Left",
    "distance": 7,
    "percent": 95,
    "gathertime": 10,
    "convert": "Walk",
}

PATTERN_SIZE_SCALE = {
    "XS": 0.25,
    "S": 0.5,
    "M": 1.0,
    "L": 1.5,
    "XL": 2.0,
}


def walk_duration_ms(tiles: float, movespeed: float = DEFAULT_MOVESPEED) -> float:
    if movespeed <= 0:
        raise ValueError("Move speed must be greater than 0")
    if tiles < 0:
        raise ValueError("Tile count cannot be negative")
    return TILE_MS / movespeed * tiles


def hive_to_ramp_tiles(hive_slot: int) -> float:
    """Tiles walked from a hive pad to the red cannon ramp.

    Natro: ``nm_Walk(5, Fwd)`` then ``nm_Walk(9.2 * HiveSlot - 4, Right)``.
    """
    if hive_slot < 1 or hive_slot > 6:
        raise ValueError("Hive slot must be between 1 and 6")
    return 5.0 + (9.2 * hive_slot - 4.0)


def _step(kind: str, **payload: Any) -> dict[str, Any]:
    return {"kind": kind, **payload}


def walk_to_pine_steps() -> list[dict[str, Any]]:
    """Walk-only route from the hive ramp to Pine Tree field (gtf-pinetree walk)."""
    return [
        _step("gotoramp"),
        _step("walk", tiles=67.5, keys=("back", "left")),
        _step("rotate", direction="right", count=4),
        _step("walk", tiles=31, keys=("fwd",)),
        _step("walk", tiles=7.8, keys=("left",)),
        _step("walk", tiles=10, keys=("back",)),
        _step("walk", tiles=5, keys=("right",)),
        _step("walk", tiles=1.5, keys=("fwd",)),
        _step("walk", tiles=60, keys=("left",)),
        _step("walk", tiles=3.75, keys=("right",)),
        _step("walk", tiles=38, keys=("fwd",)),
        _step("walk", tiles=33, keys=("left", "fwd")),
        _step("sleep", ms=200),
    ]


def cannon_to_pine_steps() -> list[dict[str, Any]]:
    """Cannon glider route from hive to Pine Tree field (gtf-pinetree cannon)."""
    return [
        _step("gotoramp"),
        _step("gotocannon"),
        _step("key", keys=("e",), hold_ms=100),
        _step("key_down", keys=("right", "back")),
        _step("sleep", ms=925),
        _step("key", keys=("space",), taps=2),
        _step("sleep", ms=4500),
        _step("key_up", keys=("back",)),
        _step("sleep", ms=500),
        _step("key_up", keys=("right",)),
        _step("key", keys=("space",), taps=1),
        _step("rotate", direction="left", count=4),
        _step("sleep", ms=2000),
    ]


def walk_from_pine_steps(hive_slot: int = 3) -> list[dict[str, Any]]:
    """Walk from Pine Tree field back toward the hive (wf-pinetree walk branch)."""
    if hive_slot < 1 or hive_slot > 6:
        raise ValueError("Hive slot must be between 1 and 6")
    steps: list[dict[str, Any]] = [
        _step("walk", tiles=31, keys=("fwd",)),
        _step("walk", tiles=75, keys=("right",)),
        _step("rotate", direction="left", count=4),
        _step("sleep", ms=50),
        _step("walk", tiles=20, keys=("fwd",)),
        _step("walk", tiles=3, keys=("fwd", "left")),
        _step("walk", tiles=18, keys=("fwd",)),
        _step("walk", tiles=6, keys=("fwd", "right")),
        _step("walk", tiles=10, keys=("right",)),
        _step("walk", tiles=2, keys=("left",)),
        _step("key_down", keys=("fwd",)),
        _step("walk", tiles=6, keys=()),
        _step("key", keys=("space",), hold_ms=200),
        _step("walk", tiles=108, keys=()),
        _step("key_up", keys=("fwd",)),
    ]
    if hive_slot == 3:
        steps.append(_step("walk", tiles=2.7, keys=("back",)))
    else:
        steps.extend(
            [
                _step("walk", tiles=1.5, keys=("back",)),
                _step("walk", tiles=35, keys=("right",)),
                _step("walk", tiles=2.7, keys=("back",)),
            ]
        )
    return steps


def gather_pattern_steps(pattern: str, size_name: str = "M", reps: int = 3) -> list[dict[str, Any]]:
    """Simplified gather patterns (tile walks in the field)."""
    size = PATTERN_SIZE_SCALE.get(size_name.upper(), 1.0)
    if reps < 1:
        raise ValueError("Pattern reps must be at least 1")
    name = pattern.replace(" ", "")
    steps: list[dict[str, Any]] = []
    if name.lower() == "stationary":
        steps.append(_step("sleep", ms=10000))
        return steps
    if name.lower() == "squares":
        for i in range(1, reps + 1):
            length = 5 * size + i
            steps.extend(
                [
                    _step("walk", tiles=length, keys=("fwd",)),
                    _step("walk", tiles=length, keys=("left",)),
                    _step("walk", tiles=length, keys=("back",)),
                    _step("walk", tiles=length, keys=("right",)),
                ]
            )
        return steps
    if name.lower() == "snake":
        for _ in range(reps):
            steps.extend(
                [
                    _step("walk", tiles=11 * size, keys=("left",)),
                    _step("walk", tiles=1, keys=("fwd",)),
                    _step("walk", tiles=11 * size, keys=("right",)),
                    _step("walk", tiles=1, keys=("fwd",)),
                ]
            )
        return steps
    if name.lower() == "lines":
        for _ in range(reps):
            steps.extend(
                [
                    _step("walk", tiles=11 * size, keys=("fwd",)),
                    _step("walk", tiles=1, keys=("left",)),
                    _step("walk", tiles=11 * size, keys=("back",)),
                    _step("walk", tiles=1, keys=("left",)),
                ]
            )
        return steps
    # Default Pine Tree pattern: CornerXSnake-style box
    for _ in range(reps):
        steps.extend(
            [
                _step("walk", tiles=4 * size, keys=("left",)),
                _step("walk", tiles=2 * size, keys=("fwd",)),
                _step("walk", tiles=8 * size, keys=("right",)),
                _step("walk", tiles=2 * size, keys=("fwd",)),
                _step("walk", tiles=8 * size, keys=("left",)),
                _step("walk", tiles=8 * size, keys=("right", "back")),
                _step("walk", tiles=8 * size, keys=("left",)),
            ]
        )
    return steps


def total_walk_tiles(steps: list[dict[str, Any]]) -> float:
    return sum(float(step.get("tiles", 0) or 0) for step in steps if step.get("kind") == "walk")
