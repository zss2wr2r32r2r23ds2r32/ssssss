"""Shared pine-collector logic used by tests and the desktop simulator."""

from .game import BSS_DEEPLINK, BSS_PLACE_ID, BSS_WEB_PAGE, launch_urls
from .license_keys import LicenseError, LicenseStore, hash_license_key, normalize_key
from .loop import PineCollector, PineCollectorConfig
from .paths import (
    DEFAULT_MOVESPEED,
    TILE_MS,
    cannon_to_pine_steps,
    gather_pattern_steps,
    hive_to_ramp_tiles,
    walk_duration_ms,
    walk_from_pine_steps,
    walk_to_pine_steps,
)

__all__ = [
    "LicenseError",
    "LicenseStore",
    "PineCollector",
    "PineCollectorConfig",
    "DEFAULT_MOVESPEED",
    "TILE_MS",
    "BSS_DEEPLINK",
    "BSS_PLACE_ID",
    "BSS_WEB_PAGE",
    "hash_license_key",
    "launch_urls",
    "normalize_key",
    "cannon_to_pine_steps",
    "gather_pattern_steps",
    "hive_to_ramp_tiles",
    "walk_duration_ms",
    "walk_from_pine_steps",
    "walk_to_pine_steps",
]
