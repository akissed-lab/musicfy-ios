#!/usr/bin/env python3
"""Static verification and port-progress report for the Musicfy iOS port.

Runs on any platform; no Xcode required. It does not replace a real build.
"""

from __future__ import annotations

import argparse
import re
from pathlib import Path

# Imports that cannot compile in commonMain of a Kotlin Multiplatform project.
UNSUPPORTED_PREFIXES = (
    "android.",
    "androidx.compose.ui.platform.LocalContext",
    "androidx.core.",
    "androidx.activity.",
    "androidx.appcompat.",
    "androidx.work.",
    "androidx.media3.",
    "androidx.media.",
    "androidx.webkit.",
    "androidx.localbroadcastmanager.",
    "androidx.documentfile.",
    "com.google.android.",
    "com.google.firebase.",
    "dalvik.",
)


def kotlin_files(root: Path) -> list[Path]:
    return sorted(p for p in root.rglob("*.kt") if "build" not in p.parts)


def balanced(source: str) -> bool:
    """Check brace balance in one pass, skipping comments, strings and raw strings.

    Comments must be handled together with strings: `https://` inside a string
    literal must not be mistaken for a line comment.
    """
    depth = 0
    in_string = False
    in_char = False
    escaped = False
    i = 0
    while i < len(source):
        if not in_string and not in_char:
            if source.startswith("//", i):
                while i < len(source) and source[i] != "\n":
                    i += 1
                continue
            if source.startswith("/*", i):
                end = source.find("*/", i + 2)
                if end < 0:
                    return False
                i = end + 2
                continue
            if source.startswith('"""', i):
                end = source.find('"""', i + 3)
                if end < 0:
                    return False
                i = end + 3
                continue
        ch = source[i]
        if escaped:
            escaped = False
        elif ch == "\\" and (in_string or in_char):
            escaped = True
        elif ch == '"' and not in_char:
            in_string = not in_string
        elif ch == "'" and not in_string:
            in_char = not in_char
        elif not in_string and not in_char:
            if ch == "{":
                depth += 1
            elif ch == "}":
                depth -= 1
                if depth < 0:
                    return False
        i += 1
    return depth == 0 and not in_string and not in_char


def scan(root: Path) -> tuple[int, list[str]]:
    warnings: list[str] = []
    files = kotlin_files(root)
    for path in files:
        text = path.read_text(encoding="utf-8", errors="replace")
        if not balanced(text):
            warnings.append(f"UNBALANCED_BRACES {path.relative_to(root)}")
        for line_no, line in enumerate(text.splitlines(), 1):
            stripped = line.strip()
            if not stripped.startswith("import "):
                continue
            name = stripped.removeprefix("import ").split(" as ")[0].rstrip()
            if any(name.startswith(prefix) for prefix in UNSUPPORTED_PREFIXES):
                warnings.append(f"ANDROID_IMPORT {path.relative_to(root)}:{line_no} {name}")
    return len(files), warnings


def write_report(root: Path, ported: int, reference: int, warnings: list[str]) -> None:
    report = root / "docs" / "04-port-status.md"
    report.parent.mkdir(parents=True, exist_ok=True)
    percent = 100 * ported / reference if reference else 0.0
    lines = [
        "# Port status",
        "",
        f"- Reference Kotlin files: **{reference}**",
        f"- Kotlin files in `composeApp`: **{ported}** ({percent:.1f}% by file count)",
        "",
        "File count is a coarse metric: it does not account for file size or",
        "platform-specific rewrites. A real build on macOS is still required.",
        "",
        "## Static warnings",
        "",
    ]
    if warnings:
        lines += [f"- `{w}`" for w in warnings[:500]]
        if len(warnings) > 500:
            lines.append(f"- ... and {len(warnings) - 500} more")
    else:
        lines.append("None.")
    report.write_text("\n".join(lines) + "\n", encoding="utf-8")


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--root", type=Path, default=Path(__file__).resolve().parent.parent)
    args = parser.parse_args()
    root = args.root

    ported_count, warnings = scan(root / "composeApp" / "src")
    reference_count = len(kotlin_files(root / "reference"))
    write_report(root, ported_count, reference_count, warnings)
    print(f"ported={ported_count} reference={reference_count} warnings={len(warnings)}")
    print(f"report={root / 'docs' / '04-port-status.md'}")


if __name__ == "__main__":
    main()
