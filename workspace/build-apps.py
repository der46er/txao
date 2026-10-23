#!/usr/bin/env python3
"""Builds workspace/apps.json from the one-file-per-app entries in workspace/apps.d/.

Run `python3 workspace/build-apps.py` after editing an entry. CI also runs it on main.
Never edit apps.json by hand: it is generated, and editing it causes merge conflicts.
"""
import json
import pathlib

here = pathlib.Path(__file__).resolve().parent
apps = [json.loads(p.read_text(encoding="utf-8")) for p in sorted((here / "apps.d").glob("*.json"))]
# Available apps first, then by optional "order", then by name.
apps.sort(key=lambda a: (a.get("status") != "available", a.get("order", 100), a["name"].lower()))
(here / "apps.json").write_text(json.dumps({"apps": apps}, indent=2) + "\n", encoding="utf-8")
print(f"apps.json: {len(apps)} apps")
