"""Pine pollen collection loop used by the simulator and unit tests."""

from __future__ import annotations

from dataclasses import dataclass
from typing import Callable

from .paths import (
    cannon_to_pine_steps,
    gather_pattern_steps,
    hive_to_ramp_tiles,
    walk_from_pine_steps,
    walk_to_pine_steps,
)


@dataclass
class PineCollectorConfig:
    hive_slot: int = 3
    movespeed: float = 28.0
    move_method: str = "Walk"  # Walk or Cannon
    gather_minutes: float = 10.0
    pattern: str = "CornerXSnake"
    pattern_size: str = "M"
    pattern_reps: int = 3
    hive_bees: int = 50
    place_sprinkler: bool = True
    convert_after_gather: bool = True


@dataclass
class LoopEvent:
    action: str
    detail: str = ""


class PineCollector:
    """State machine for: reset -> convert -> pine -> gather -> hive -> convert."""

    def __init__(
        self,
        config: PineCollectorConfig | None = None,
        on_event: Callable[[LoopEvent], None] | None = None,
    ):
        self.config = config or PineCollectorConfig()
        self.on_event = on_event
        self.running = False
        self.paused = False
        self.cycles_completed = 0
        self.last_events: list[LoopEvent] = []

    def _emit(self, action: str, detail: str = "") -> LoopEvent:
        event = LoopEvent(action=action, detail=detail)
        self.last_events.append(event)
        if self.on_event:
            self.on_event(event)
        return event

    def validate_config(self) -> None:
        cfg = self.config
        if cfg.hive_slot < 1 or cfg.hive_slot > 6:
            raise ValueError("Hive slot must be between 1 and 6")
        if cfg.movespeed <= 0:
            raise ValueError("Move speed must be greater than 0")
        if cfg.gather_minutes <= 0:
            raise ValueError("Gather time must be greater than 0")
        method = cfg.move_method.strip().title()
        if method not in {"Walk", "Cannon"}:
            raise ValueError("Move method must be Walk or Cannon")
        cfg.move_method = method

    def describe_cycle(self) -> list[LoopEvent]:
        """Return the actions one collection cycle will perform, without running them."""
        self.validate_config()
        events: list[LoopEvent] = []

        def add(action: str, detail: str = "") -> None:
            events.append(LoopEvent(action, detail))

        add("reset", "Esc + R + Enter to respawn at hive")
        add("convert", "Press E at hive to convert pollen")
        add(
            "travel_ramp",
            f"Walk {hive_to_ramp_tiles(self.config.hive_slot):.1f} tiles from hive {self.config.hive_slot} to ramp",
        )
        if self.config.move_method == "Cannon":
            add("travel_pine", f"{len(cannon_to_pine_steps())} cannon steps to Pine Tree")
        else:
            tiles = sum(s.get("tiles", 0) or 0 for s in walk_to_pine_steps() if s["kind"] == "walk")
            add("travel_pine", f"Walk {tiles:.1f} tiles to Pine Tree")
        if self.config.place_sprinkler:
            add("sprinkler", "Press 1 to place sprinkler")
        add(
            "gather",
            f"{self.config.pattern} x{self.config.pattern_reps} for {self.config.gather_minutes:g} min",
        )
        add("return_hive", "Walk back from Pine Tree toward hive")
        if self.config.convert_after_gather:
            add("convert", "Convert backpack at hive")
        return events

    def run_cycle(self) -> list[LoopEvent]:
        """Execute one simulated collection cycle (no game input)."""
        self.validate_config()
        started_at = len(self.last_events)
        for event in self.describe_cycle():
            self._emit(event.action, event.detail)
        # Touch path builders so tests can assert they stay in sync
        _ = walk_to_pine_steps()
        _ = cannon_to_pine_steps()
        _ = walk_from_pine_steps(self.config.hive_slot)
        _ = gather_pattern_steps(
            self.config.pattern, self.config.pattern_size, self.config.pattern_reps
        )
        self.cycles_completed += 1
        self._emit("cycle_complete", f"Cycle {self.cycles_completed} finished")
        return self.last_events[started_at:]
