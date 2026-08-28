from __future__ import annotations

import unittest
from pathlib import Path

from pine_core.game import (
    BSS_DEEPLINK,
    BSS_PLACE_ID,
    BSS_WEB_PAGE,
    BSS_WEB_START,
    launch_urls,
)
from pine_core.license_keys import TESTING_LICENSE_KEY, hash_license_key

ROOT = Path(__file__).resolve().parent.parent
LAUNCHER = ROOT / "launcher" / "PinePollenLauncher.c"
EXE = ROOT / "dist" / "PinePollenMacro.exe"


class GameLinkTests(unittest.TestCase):
    def test_place_id_is_bee_swarm_simulator(self) -> None:
        self.assertEqual(BSS_PLACE_ID, 1537690962)
        self.assertIn(str(BSS_PLACE_ID), BSS_DEEPLINK)
        self.assertIn("roblox://experiences/start", BSS_DEEPLINK)
        self.assertIn("Bee-Swarm-Simulator", BSS_WEB_PAGE)

    def test_launch_order_tries_protocol_then_web(self) -> None:
        urls = launch_urls()
        self.assertEqual(urls[0], BSS_DEEPLINK)
        self.assertEqual(urls[1], BSS_WEB_START)
        self.assertEqual(urls[2], BSS_WEB_PAGE)

    def test_c_launcher_embeds_same_urls_and_test_hash(self) -> None:
        source = LAUNCHER.read_text(encoding="utf-8")
        self.assertIn(BSS_DEEPLINK, source)
        self.assertIn(str(BSS_PLACE_ID), source)
        self.assertIn(BSS_WEB_PAGE, source)
        digest = hash_license_key(TESTING_LICENSE_KEY)
        self.assertIn(digest, source)
        self.assertIn("admintest123", source)
        self.assertIn("ShellExecuteA", source)
        self.assertNotIn("WriteProcessMemory", source)

    def test_ahk_macro_can_open_the_game(self) -> None:
        ahk = (ROOT / "pine_macro.ahk").read_text(encoding="utf-8")
        self.assertIn("LaunchBeeSwarm", ahk)
        self.assertIn(BSS_DEEPLINK, ahk)
        self.assertIn("Open Bee Swarm Simulator", ahk)

    def test_windows_exe_is_pe32(self) -> None:
        self.assertTrue(EXE.is_file(), "PinePollenMacro.exe was not built")
        header = EXE.read_bytes()[:2]
        self.assertEqual(header, b"MZ")
        self.assertGreater(EXE.stat().st_size, 8_000)


if __name__ == "__main__":
    unittest.main()
