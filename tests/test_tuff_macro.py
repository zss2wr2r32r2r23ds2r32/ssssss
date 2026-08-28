from __future__ import annotations

import unittest
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TUFF = ROOT / "TuffMacro"
ZIP = ROOT / "dist" / "TuffMacro.zip"


class TuffMacroRebrandTests(unittest.TestCase):
    def test_tree_exists(self) -> None:
        self.assertTrue((TUFF / "START.bat").is_file())
        self.assertTrue((TUFF / "submacros" / "natro_macro.ahk").is_file())
        self.assertTrue((TUFF / "submacros" / "AutoHotkey32.exe").is_file())
        self.assertTrue((TUFF / "nm_image_assets" / "Styles" / "Tuff.msstyles").is_file())
        self.assertTrue((TUFF / "nm_image_assets" / "tuff.ico").is_file())
        self.assertTrue((TUFF / "nm_image_assets" / "tuff.png").is_file())

    def test_start_bat_uses_tuff_name(self) -> None:
        text = (TUFF / "START.bat").read_text(encoding="utf-8")
        self.assertIn("Starting Tuff Macro", text)
        self.assertNotIn("Natro Macro", text)

    def test_window_title_is_tuff_macro(self) -> None:
        text = (TUFF / "submacros" / "natro_macro.ahk").read_text(encoding="utf-8")
        self.assertIn('MainGui.Title := "Tuff Macro"', text)
        self.assertIn('Tuff Macro (Loading 0%)', text)
        self.assertNotIn("Natro Macro (Loading", text)

    def test_heartbeat_matches_tuff_window(self) -> None:
        text = (TUFF / "submacros" / "Heartbeat.ahk").read_text(encoding="utf-8")
        self.assertIn("Tuff ahk_class AutoHotkeyGUI", text)
        self.assertNotIn("Natro ahk_class AutoHotkeyGUI", text)
        # Script filename is unchanged so the existing process checks still work.
        self.assertIn("natro_macro ahk_class AutoHotkey", text)

    def test_planter_timers_match_tuff_window(self) -> None:
        text = (TUFF / "submacros" / "PlanterTimers.ahk").read_text(encoding="utf-8")
        self.assertIn("Tuff ahk_class AutoHotkeyGUI", text)
        self.assertNotIn("Natro ahk_class AutoHotkeyGUI", text)

    def test_default_theme_is_black_and_purple(self) -> None:
        macro = (TUFF / "submacros" / "natro_macro.ahk").read_text(encoding="utf-8")
        timers = (TUFF / "submacros" / "PlanterTimers.ahk").read_text(encoding="utf-8")
        self.assertIn('GuiTheme", "Tuff"', macro)
        self.assertIn('GuiTheme", "Tuff")', timers)
        self.assertIn('MainGui.BackColor := "0x0A0014"', macro)
        self.assertIn('TimersGui.BackColor := "0x0A0014"', timers)
        self.assertIn("cC9B6FF", macro)
        self.assertIn("cC9B6FF", timers)
        self.assertNotIn("cDefault", macro)
        self.assertNotIn("cDefault", timers)

    def test_tray_and_window_use_tuff_icon(self) -> None:
        macro = (TUFF / "submacros" / "natro_macro.ahk").read_text(encoding="utf-8")
        self.assertIn('TraySetIcon "nm_image_assets\\tuff.ico"', macro)
        self.assertNotIn('TraySetIcon "nm_image_assets\\auryn.ico"', macro)
        self.assertIn("nm_ApplyTuffIcon", macro)
        ico = (TUFF / "nm_image_assets" / "tuff.ico").read_bytes()
        self.assertEqual(ico[:4], b"\x00\x00\x01\x00")
        self.assertGreater((TUFF / "nm_image_assets" / "tuff.ico").stat().st_size, 1000)

    def test_github_urls_still_credit_natro(self) -> None:
        text = (TUFF / "submacros" / "natro_macro.ahk").read_text(encoding="utf-8")
        self.assertIn("NatroTeam/NatroMacro", text)
        self.assertIn("modified copy of Natro Macro", text)

    def test_release_zip_contains_tuff_macro(self) -> None:
        self.assertTrue(ZIP.is_file(), "TuffMacro.zip was not packed")
        with zipfile.ZipFile(ZIP) as zf:
            names = set(zf.namelist())
        self.assertIn("TuffMacro/START.bat", names)
        self.assertIn("TuffMacro/submacros/natro_macro.ahk", names)
        self.assertIn("TuffMacro/submacros/AutoHotkey32.exe", names)
        self.assertIn("TuffMacro/nm_image_assets/Styles/Tuff.msstyles", names)
        self.assertIn("TuffMacro/NOTICE.md", names)
        self.assertIn("TuffMacro/nm_image_assets/tuff.ico", names)
        self.assertIn("TuffMacro/nm_image_assets/tuff.png", names)

    def test_paths_and_patterns_were_not_rebranded(self) -> None:
        for folder in ("paths", "patterns"):
            files = list((TUFF / folder).rglob("*.ahk"))
            self.assertGreater(len(files), 0, folder)
            for path in files:
                text = path.read_text(encoding="utf-8", errors="replace")
                self.assertNotIn("Tuff Macro", text, path)
                self.assertNotIn("cC9B6FF", text, path)
                self.assertNotIn("0x0A0014", text, path)
