from __future__ import annotations

import re
import unittest
from pathlib import Path

from pine_core.paths import (
    DEFAULT_MOVESPEED,
    PINE_DEFAULTS,
    cannon_to_pine_steps,
    gather_pattern_steps,
    hive_to_ramp_tiles,
    total_walk_tiles,
    walk_duration_ms,
    walk_from_pine_steps,
    walk_to_pine_steps,
)

ROOT = Path(__file__).resolve().parent.parent
AHK_PATHS = (ROOT / "paths" / "pinetree.ahk").read_text(encoding="utf-8")
AHK_WALK = (ROOT / "lib" / "Walk.ahk").read_text(encoding="utf-8")
AHK_MACRO = (ROOT / "pine_macro.ahk").read_text(encoding="utf-8")


class PathAndWalkTests(unittest.TestCase):
    def test_walk_duration_matches_natro_legacy_formula(self) -> None:
        self.assertAlmostEqual(walk_duration_ms(1, 28), 4000 / 28)
        self.assertAlmostEqual(walk_duration_ms(67.5, 28), 4000 / 28 * 67.5)
        self.assertIn("4000 / speed * tiles", AHK_WALK)

    def test_zero_movespeed_rejected(self) -> None:
        with self.assertRaises(ValueError):
            walk_duration_ms(1, 0)

    def test_hive_to_ramp_tiles(self) -> None:
        self.assertAlmostEqual(hive_to_ramp_tiles(1), 5 + 9.2 * 1 - 4)
        self.assertAlmostEqual(hive_to_ramp_tiles(3), 5 + 9.2 * 3 - 4)
        self.assertAlmostEqual(hive_to_ramp_tiles(6), 5 + 9.2 * 6 - 4)
        self.assertIn("9.2 * HiveSlot - 4", AHK_WALK)
        with self.assertRaises(ValueError):
            hive_to_ramp_tiles(0)
        with self.assertRaises(ValueError):
            hive_to_ramp_tiles(7)

    def test_walk_to_pine_contains_natro_tile_counts(self) -> None:
        tiles = [step["tiles"] for step in walk_to_pine_steps() if step["kind"] == "walk"]
        self.assertEqual(tiles[:4], [67.5, 31, 7.8, 10])
        self.assertIn(60, tiles)
        self.assertIn(38, tiles)
        self.assertIn(33, tiles)
        for value in ("67.5", "7.8", "3.75"):
            self.assertIn(value, AHK_PATHS)

    def test_cannon_route_uses_e_and_glide_timings(self) -> None:
        steps = cannon_to_pine_steps()
        kinds = [step["kind"] for step in steps]
        self.assertIn("gotocannon", kinds)
        sleeps = [step["ms"] for step in steps if step["kind"] == "sleep"]
        self.assertIn(925, sleeps)
        self.assertIn(4500, sleeps)
        self.assertIn("HyperSleep(925)", AHK_PATHS)
        self.assertIn("HyperSleep(4500)", AHK_PATHS)

    def test_walk_from_pine_hive_slot_3_is_shorter(self) -> None:
        slot3 = total_walk_tiles(walk_from_pine_steps(3))
        slot1 = total_walk_tiles(walk_from_pine_steps(1))
        self.assertLess(slot3, slot1)
        self.assertIn("HiveSlot = 3", AHK_PATHS)

    def test_pine_defaults_match_natro_field_config(self) -> None:
        self.assertEqual(PINE_DEFAULTS["pattern"], "CornerXSnake")
        self.assertEqual(PINE_DEFAULTS["gathertime"], 10)
        self.assertEqual(PINE_DEFAULTS["percent"], 95)
        self.assertIn('PatternName := "CornerXSnake"', AHK_MACRO)
        self.assertIn("GatherMinutes := 10", AHK_MACRO)

    def test_gather_patterns_produce_steps(self) -> None:
        squares = gather_pattern_steps("Squares", "M", 2)
        self.assertEqual(len(squares), 8)
        snake = gather_pattern_steps("Snake", "S", 1)
        self.assertTrue(all(s["kind"] == "walk" for s in snake))
        stationary = gather_pattern_steps("Stationary")
        self.assertEqual(stationary[0]["kind"], "sleep")
        with self.assertRaises(ValueError):
            gather_pattern_steps("Squares", "M", 0)

    def test_ahk_macro_is_pine_only(self) -> None:
        self.assertIn("GoToPineTree()", AHK_MACRO)
        self.assertIn("WalkFromPineTree()", AHK_MACRO)
        self.assertNotIn("gtf-strawberry", AHK_MACRO)
        self.assertRegex(AHK_PATHS, re.compile(r"function GoToPineTree|GoToPineTree\(\)"))


if __name__ == "__main__":
    unittest.main()
