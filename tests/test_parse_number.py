from __future__ import annotations

import unittest

from pine_core.parse_number import parse_integer, parse_number


class ParseNumberTests(unittest.TestCase):
    def test_plain_digits(self) -> None:
        self.assertEqual(parse_number("28", 16), 28.0)
        self.assertEqual(parse_number("10", 1), 10.0)

    def test_empty_and_whitespace_use_fallback(self) -> None:
        self.assertEqual(parse_number("", 28), 28.0)
        self.assertEqual(parse_number("   ", 28), 28.0)
        self.assertEqual(parse_number(None, 28), 28.0)

    def test_garbage_uses_fallback(self) -> None:
        self.assertEqual(parse_number("abc", 28), 28.0)
        self.assertEqual(parse_number(",,,,1", 28), 1.0)

    def test_strips_commas(self) -> None:
        self.assertEqual(parse_number("1,028", 28), 1028.0)

    def test_hive_slot_clamped(self) -> None:
        self.assertEqual(parse_integer("6", 3, 1, 6), 6)
        self.assertEqual(parse_integer("0", 3, 1, 6), 1)
        self.assertEqual(parse_integer("99", 3, 1, 6), 6)
        self.assertEqual(parse_integer("", 3, 1, 6), 3)

    def test_ahk_macro_does_not_call_number_on_edit_strings(self) -> None:
        from pathlib import Path

        ahk = (Path(__file__).resolve().parent.parent / "pine_macro.ahk").read_text(encoding="utf-8")
        self.assertIn("ParseNumber(", ahk)
        self.assertNotIn("MoveSpeedNum := Number(", ahk)
        self.assertNotIn("HiveSlot := Integer(hiveEdit.Value", ahk)
        self.assertIn("cBlack", ahk)


if __name__ == "__main__":
    unittest.main()
