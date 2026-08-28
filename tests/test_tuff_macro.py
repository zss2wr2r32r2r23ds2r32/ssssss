from __future__ import annotations

import hashlib
import json
import unittest
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
TUFF = ROOT / "TuffMacro"
ZIP = ROOT / "dist" / "TuffMacro.zip"
MACRO = TUFF / "submacros" / "tuff.ahk"


class TuffMacroRebrandTests(unittest.TestCase):
    def test_tree_exists(self) -> None:
        self.assertTrue((TUFF / "START.bat").is_file())
        self.assertTrue(MACRO.is_file())
        self.assertFalse((TUFF / "submacros" / "natro_macro.ahk").exists())
        self.assertTrue((TUFF / "submacros" / "AutoHotkey32.exe").is_file())
        self.assertTrue((TUFF / "nm_image_assets" / "Styles" / "Tuff.msstyles").is_file())
        self.assertTrue((TUFF / "nm_image_assets" / "tuff.ico").is_file())
        self.assertTrue((TUFF / "lib" / "License.ahk").is_file())
        self.assertTrue((TUFF / "licenses.json").is_file())

    def test_start_bat_launches_tuff_ahk(self) -> None:
        text = (TUFF / "START.bat").read_text(encoding="utf-8")
        self.assertIn("submacros\\tuff.ahk", text)
        self.assertNotIn("natro_macro.ahk", text)
        self.assertIn("Starting Tuff Macro", text)

    def test_window_title_is_tuff_macro(self) -> None:
        text = MACRO.read_text(encoding="utf-8")
        self.assertIn('MainGui.Title := "Tuff Macro"', text)
        self.assertIn('Tuff Macro (Loading 0%)', text)
        self.assertNotIn("Natro Macro (Loading", text)

    def test_heartbeat_matches_tuff_script(self) -> None:
        text = (TUFF / "submacros" / "Heartbeat.ahk").read_text(encoding="utf-8")
        self.assertIn("Tuff ahk_class AutoHotkeyGUI", text)
        self.assertIn("tuff.ahk", text)
        self.assertIn("tuff ahk_class AutoHotkey", text)
        self.assertNotIn("natro_macro", text)

    def test_ui_is_blue_and_taller(self) -> None:
        macro = MACRO.read_text(encoding="utf-8")
        timers = (TUFF / "submacros" / "PlanterTimers.ahk").read_text(encoding="utf-8")
        self.assertIn('MainGui.BackColor := "0x071422"', macro)
        self.assertIn('TimersGui.BackColor := "0x071422"', timers)
        self.assertIn("c7EC8FF", macro)
        self.assertIn("c7EC8FF", timers)
        self.assertIn('w540 h392', macro)
        self.assertIn("QuickHiveSlot", macro)
        self.assertIn("QuickMoveSpeed", macro)
        self.assertNotIn("cC9B6FF", macro)
        self.assertNotIn("0x0A0014", macro)

    def test_rainbow_webhook_removed(self) -> None:
        macro = MACRO.read_text(encoding="utf-8")
        status = (TUFF / "submacros" / "Status.ahk").read_text(encoding="utf-8")
        self.assertNotIn("Enable Rainbow Webhook", macro)
        self.assertNotIn("nm_WebhookEasterEgg()", macro)
        self.assertIn("WebhookEasterEgg := 0", status)

    def test_license_box_hides_the_key(self) -> None:
        license_ahk = (TUFF / "lib" / "License.ahk").read_text(encoding="utf-8")
        macro = MACRO.read_text(encoding="utf-8")
        self.assertIn("License.PromptAndActivate", macro)
        self.assertIn("Password", license_ahk)
        self.assertNotIn("charliesmacro", license_ahk)
        self.assertNotIn("charliesmacro", macro)
        self.assertNotIn("Testing key", license_ahk)
        data = json.loads((TUFF / "licenses.json").read_text(encoding="utf-8"))
        digest = hashlib.sha256(f"{data['salt']}:charliesmacro".encode()).hexdigest()
        hashes = [item["hash"] for item in data["keys"]]
        self.assertIn(digest, hashes)
        self.assertEqual(data["salt"], "tuff-macro-v1")

    def test_tray_uses_tuff_icon(self) -> None:
        macro = MACRO.read_text(encoding="utf-8")
        self.assertIn('TraySetIcon "nm_image_assets\\tuff.ico"', macro)
        ico = (TUFF / "nm_image_assets" / "tuff.ico").read_bytes()
        self.assertEqual(ico[:4], b"\x00\x00\x01\x00")

    def test_github_urls_still_credit_natro(self) -> None:
        text = MACRO.read_text(encoding="utf-8")
        self.assertIn("NatroTeam/NatroMacro", text)
        self.assertIn("modified copy of Natro Macro", text)

    def test_release_zip_contains_tuff_macro(self) -> None:
        self.assertTrue(ZIP.is_file(), "TuffMacro.zip was not packed")
        with zipfile.ZipFile(ZIP) as zf:
            names = set(zf.namelist())
        self.assertIn("TuffMacro/START.bat", names)
        self.assertIn("TuffMacro/submacros/tuff.ahk", names)
        self.assertNotIn("TuffMacro/submacros/natro_macro.ahk", names)
        self.assertIn("TuffMacro/lib/License.ahk", names)
        self.assertIn("TuffMacro/licenses.json", names)
        self.assertIn("TuffMacro/submacros/AutoHotkey32.exe", names)
        self.assertIn("TuffMacro/nm_image_assets/tuff.ico", names)

    def test_paths_and_patterns_were_not_rebranded(self) -> None:
        for folder in ("paths", "patterns"):
            files = list((TUFF / folder).rglob("*.ahk"))
            self.assertGreater(len(files), 0, folder)
            for path in files:
                text = path.read_text(encoding="utf-8", errors="replace")
                self.assertNotIn("Tuff Macro", text, path)
                self.assertNotIn("charliesmacro", text, path)
