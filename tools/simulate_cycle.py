#!/usr/bin/env python3
"""Print one simulated Pine Tree collection cycle."""

from __future__ import annotations

import argparse
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

from pine_core.loop import PineCollector, PineCollectorConfig


def main() -> int:
    parser = argparse.ArgumentParser(description="Simulate one pine collection cycle")
    parser.add_argument("--hive-slot", type=int, default=3)
    parser.add_argument("--method", choices=["Walk", "Cannon"], default="Walk")
    parser.add_argument("--pattern", default="CornerXSnake")
    parser.add_argument("--minutes", type=float, default=10)
    args = parser.parse_args()
    collector = PineCollector(
        PineCollectorConfig(
            hive_slot=args.hive_slot,
            move_method=args.method,
            pattern=args.pattern,
            gather_minutes=args.minutes,
        )
    )
    for event in collector.run_cycle():
        print(f"{event.action}: {event.detail}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
