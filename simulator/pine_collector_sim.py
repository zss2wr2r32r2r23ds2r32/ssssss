#!/usr/bin/env python3
"""Desktop simulator for Pine Pollen Macro (license gate + pine loop).

This mirrors the AutoHotkey UI so the license key and collection cycle can be
exercised without Roblox or Windows.
"""

from __future__ import annotations

import sys
import tkinter as tk
from pathlib import Path
from tkinter import messagebox, ttk

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT))

from pine_core.license_keys import TESTING_LICENSE_KEY, LicenseError, LicenseStore
from pine_core.loop import PineCollector, PineCollectorConfig

BG = "#141414"
FG = "#f2f2f2"
ACCENT = "#faa125"
MUTED = "#9a9a9a"
ENTRY_BG = "#1f1f1f"
BTN_BG = "#2a2a2a"


class PineSimulator(tk.Tk):
    def __init__(self) -> None:
        super().__init__()
        self.title("Pine Pollen Macro")
        self.configure(bg=BG)
        self.resizable(False, False)
        self.store = LicenseStore()
        self.collector: PineCollector | None = None
        self._build_license()

    def _style_entry(self, entry: tk.Entry) -> None:
        entry.configure(
            bg=ENTRY_BG,
            fg=FG,
            insertbackground=FG,
            relief="flat",
            highlightthickness=1,
            highlightbackground="#333333",
        )

    def _build_license(self) -> None:
        for child in self.winfo_children():
            child.destroy()
        frame = tk.Frame(self, bg=BG, padx=24, pady=20)
        frame.pack()
        tk.Label(
            frame,
            text="Pine Pollen Macro",
            bg=BG,
            fg=ACCENT,
            font=("Segoe UI", 16, "bold"),
        ).pack(anchor="w")
        tk.Label(
            frame,
            text="Enter your license key to unlock the Pine Tree collector.",
            bg=BG,
            fg=FG,
            font=("Segoe UI", 10),
            wraplength=360,
            justify="left",
        ).pack(anchor="w", pady=(8, 0))
        tk.Label(
            frame,
            text=f"Testing key: {TESTING_LICENSE_KEY}",
            bg=BG,
            fg=MUTED,
            font=("Segoe UI", 9),
        ).pack(anchor="w", pady=(4, 12))

        self.license_var = tk.StringVar()
        entry = tk.Entry(frame, textvariable=self.license_var, width=36, show="*")
        self._style_entry(entry)
        entry.pack(anchor="w", ipady=6)
        entry.focus_set()

        self.license_status = tk.Label(frame, text="", bg=BG, fg="#ff6b6b", font=("Segoe UI", 9))
        self.license_status.pack(anchor="w", pady=(8, 12))

        btns = tk.Frame(frame, bg=BG)
        btns.pack(anchor="w")
        tk.Button(
            btns,
            text="Activate",
            command=self._activate,
            bg=ACCENT,
            fg="#111111",
            relief="flat",
            padx=16,
            pady=6,
            font=("Segoe UI", 10, "bold"),
        ).pack(side="left")
        tk.Button(
            btns,
            text="Exit",
            command=self.destroy,
            bg=BTN_BG,
            fg=FG,
            relief="flat",
            padx=16,
            pady=6,
        ).pack(side="left", padx=(10, 0))
        self.bind("<Return>", lambda _e: self._activate())

    def _activate(self) -> None:
        key = self.license_var.get()
        try:
            record = self.store.validate(key)
        except LicenseError as exc:
            self.license_status.config(text=str(exc), fg="#ff6b6b")
            return
        self.license_status.config(text=f"Activated ({record.role})", fg="#6bff95")
        self.after(150, lambda: self._build_main(record.role, record.label))

    def _build_main(self, role: str, label: str) -> None:
        for child in self.winfo_children():
            child.destroy()
        frame = tk.Frame(self, bg=BG, padx=20, pady=16)
        frame.pack()
        tk.Label(frame, text="Pine Tree pollen collector", bg=BG, fg=ACCENT, font=("Segoe UI", 14, "bold")).grid(
            row=0, column=0, columnspan=4, sticky="w"
        )
        tk.Label(
            frame,
            text=f"License: {role}" + (f" — {label}" if label else ""),
            bg=BG,
            fg=MUTED,
            font=("Segoe UI", 9),
        ).grid(row=1, column=0, columnspan=4, sticky="w", pady=(0, 10))

        self.hive_var = tk.StringVar(value="3")
        self.speed_var = tk.StringVar(value="28")
        self.method_var = tk.StringVar(value="Walk")
        self.gather_var = tk.StringVar(value="10")
        self.pattern_var = tk.StringVar(value="CornerXSnake")
        self.size_var = tk.StringVar(value="M")
        self.reps_var = tk.StringVar(value="3")
        self.sprinkler_var = tk.BooleanVar(value=True)
        self.convert_var = tk.BooleanVar(value=True)

        def labeled(row: int, text: str, widget: tk.Widget) -> None:
            tk.Label(frame, text=text, bg=BG, fg=FG, font=("Segoe UI", 9)).grid(row=row, column=0, sticky="w", pady=3)
            widget.grid(row=row, column=1, columnspan=3, sticky="w", pady=3)

        hive = tk.Spinbox(frame, from_=1, to=6, textvariable=self.hive_var, width=6, bg=ENTRY_BG, fg=FG)
        labeled(2, "Hive slot", hive)
        speed = tk.Entry(frame, textvariable=self.speed_var, width=8, bg=ENTRY_BG, fg=FG)
        labeled(3, "Move speed", speed)
        method = ttk.Combobox(frame, textvariable=self.method_var, values=["Walk", "Cannon"], width=12, state="readonly")
        labeled(4, "Travel", method)
        gather = tk.Entry(frame, textvariable=self.gather_var, width=8, bg=ENTRY_BG, fg=FG)
        labeled(5, "Gather min", gather)
        pattern = ttk.Combobox(
            frame,
            textvariable=self.pattern_var,
            values=["CornerXSnake", "Squares", "Snake", "Lines", "Stationary"],
            width=16,
            state="readonly",
        )
        labeled(6, "Pattern", pattern)

        tk.Checkbutton(
            frame,
            text="Place sprinkler",
            variable=self.sprinkler_var,
            bg=BG,
            fg=FG,
            selectcolor=ENTRY_BG,
            activebackground=BG,
            activeforeground=FG,
        ).grid(row=7, column=0, sticky="w", pady=(8, 0))
        tk.Checkbutton(
            frame,
            text="Convert at hive",
            variable=self.convert_var,
            bg=BG,
            fg=FG,
            selectcolor=ENTRY_BG,
            activebackground=BG,
            activeforeground=FG,
        ).grid(row=7, column=1, columnspan=2, sticky="w", pady=(8, 0))

        btns = tk.Frame(frame, bg=BG)
        btns.grid(row=8, column=0, columnspan=4, sticky="w", pady=(12, 8))
        tk.Button(btns, text="Start (simulate cycle)", command=self._run_cycle, bg=ACCENT, fg="#111", relief="flat", padx=12, pady=5).pack(
            side="left"
        )
        tk.Button(btns, text="Change license", command=self._build_license, bg=BTN_BG, fg=FG, relief="flat", padx=12, pady=5).pack(
            side="left", padx=(8, 0)
        )

        self.log = tk.Text(frame, width=48, height=12, bg=ENTRY_BG, fg="#dcdcdc", relief="flat", state="normal")
        self.log.grid(row=9, column=0, columnspan=4, pady=(4, 0))
        self._log("Ready. This simulator does not send keys to Roblox.")
        self._log("Press Start to preview one Pine Tree collection cycle.")

    def _log(self, message: str) -> None:
        self.log.insert("end", message + "\n")
        self.log.see("end")

    def _run_cycle(self) -> None:
        try:
            config = PineCollectorConfig(
                hive_slot=int(self.hive_var.get()),
                movespeed=float(self.speed_var.get()),
                move_method=self.method_var.get(),
                gather_minutes=float(self.gather_var.get()),
                pattern=self.pattern_var.get(),
                pattern_size=self.size_var.get(),
                pattern_reps=int(self.reps_var.get()),
                place_sprinkler=self.sprinkler_var.get(),
                convert_after_gather=self.convert_var.get(),
            )
            collector = PineCollector(config, on_event=lambda event: self._log(f"{event.action}: {event.detail}"))
            collector.run_cycle()
        except (ValueError, LicenseError) as exc:
            messagebox.showerror("Pine Pollen Macro", str(exc))
            self._log(f"error: {exc}")


def main() -> int:
    app = PineSimulator()
    app.mainloop()
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
