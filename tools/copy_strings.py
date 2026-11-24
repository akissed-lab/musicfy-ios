"""Copy upstream Android string resources into Compose Multiplatform resources."""

from pathlib import Path
import re
import shutil

UPSTREAM = Path(r"C:\Users\fonk7\AppData\Local\Temp\opencode\musicfy-main\app\src\main\res\values")
TARGET = Path(__file__).resolve().parents[1] / "composeApp/src/commonMain/composeResources/values"
FILES = ["app_name.xml", "strings.xml", "musicfy_strings.xml", "updater_strings.xml"]

names = []
for name in FILES:
    text = (UPSTREAM / name).read_text(encoding="utf-8")
    names += re.findall(r'<(?:string|plurals)\s+name="([^"]+)"', text)

seen = set()
duplicates = sorted({n for n in names if n in seen or seen.add(n)})
if duplicates:
    raise SystemExit("duplicate resource names: " + ", ".join(duplicates))

TARGET.mkdir(parents=True, exist_ok=True)
for name in FILES:
    shutil.copy2(UPSTREAM / name, TARGET / name)

print(f"copied {len(names)} resources from {len(FILES)} files to {TARGET}")
