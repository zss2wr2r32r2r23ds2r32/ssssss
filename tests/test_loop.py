from __future__ import annotations

import unittest

from pine_core.loop import PineCollector, PineCollectorConfig


class LoopTests(unittest.TestCase):
    def test_cycle_order_walk_method(self) -> None:
        collector = PineCollector(PineCollectorConfig(move_method="Walk", hive_slot=3))
        actions = [event.action for event in collector.describe_cycle()]
        self.assertEqual(
            actions,
            [
                "reset",
                "convert",
                "travel_ramp",
                "travel_pine",
                "sprinkler",
                "gather",
                "return_hive",
                "convert",
            ],
        )

    def test_run_cycle_increments_counter(self) -> None:
        events_seen: list[str] = []
        collector = PineCollector(
            PineCollectorConfig(gather_minutes=1, place_sprinkler=False),
            on_event=lambda event: events_seen.append(event.action),
        )
        collector.config.convert_after_gather = True
        result = collector.run_cycle()
        self.assertEqual(collector.cycles_completed, 1)
        self.assertEqual(result[-1].action, "cycle_complete")
        self.assertIn("gather", events_seen)
        self.assertNotIn("sprinkler", [e.action for e in collector.describe_cycle()])

    def test_cannon_cycle_mentions_cannon(self) -> None:
        collector = PineCollector(PineCollectorConfig(move_method="cannon"))
        pine = next(event for event in collector.describe_cycle() if event.action == "travel_pine")
        self.assertIn("cannon", pine.detail.lower())

    def test_invalid_hive_slot_rejected(self) -> None:
        collector = PineCollector(PineCollectorConfig(hive_slot=9))
        with self.assertRaises(ValueError):
            collector.validate_config()

    def test_invalid_move_method_rejected(self) -> None:
        collector = PineCollector(PineCollectorConfig(move_method="teleport"))
        with self.assertRaises(ValueError):
            collector.validate_config()


if __name__ == "__main__":
    unittest.main()
