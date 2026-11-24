#!/usr/bin/env python3
"""List reference Kotlin files that have no Android-only imports."""
from __future__ import annotations

import re
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "reference"
BAD = re.compile(
    r"^import (android\.|androidx\.(core|activity|appcompat|work|media3|media|webkit|localbroadcastmanager|documentfile)|com\.google\.(android|firebase)|dalvik\.)"
)

items = []
for path in ROOT.rglob("*.kt"):
    text = path.read_text(encoding="utf-8", errors="ignore")
    if any(BAD.match(line) for line in text.splitlines() if line.startswith("import ")):
        continue
    items.append((len(text.splitlines()), path))

items.sort()
for lines, path in items:
    print(f"{lines:5} {path.relative_to(ROOT)}")
