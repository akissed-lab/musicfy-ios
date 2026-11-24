"""Report Kotlin/Native KLIB ABI/compiler versions for Maven dependencies.

Used to keep dependency versions compatible with the Kotlin compiler pinned in
gradle/libs.versions.toml. Requires network access but no Gradle or Xcode.
"""

from __future__ import annotations

import io
import json
import re
import sys
import urllib.parse
import urllib.request
import zipfile

DEPS = [
    ("org.jetbrains.kotlinx", "kotlinx-serialization-json", "1.10.0"),
    ("org.jetbrains.kotlinx", "kotlinx-coroutines-core", "1.11.0"),
    ("org.jetbrains.kotlinx", "kotlinx-datetime", "0.6.2"),
    ("io.ktor", "ktor-client-core", "3.4.0"),
    ("io.ktor", "ktor-client-darwin", "3.4.0"),
    ("app.cash.sqldelight", "runtime", "2.4.1"),
    ("org.jetbrains.androidx.navigation", "navigation-runtime", "2.10.0-beta01"),
    ("org.jetbrains.androidx.lifecycle", "lifecycle-viewmodel-compose", "2.10.0"),
    ("io.coil-kt.coil3", "coil-core", "3.3.0"),
]

BASE = "https://repo1.maven.org/maven2/"


def module_url(group: str, artifact: str, version: str) -> str:
    return BASE + f"{group.replace('.', '/')}/{artifact}/{version}/{artifact}-{version}.module"


def find_klib(module: dict, module_url_value: str, follow_redirect: bool = True) -> str | None:
    for variant in module.get("variants", []):
        attrs = variant.get("attributes", {})
        if attrs.get("org.jetbrains.kotlin.native.target") != "ios_simulator_arm64":
            continue
        if attrs.get("org.jetbrains.kotlin.platform.type") != "native":
            continue
        for file_info in variant.get("files", []):
            url = file_info.get("url")
            if url and url.endswith(".klib"):
                return urllib.parse.urljoin(module_url_value, url)
        if follow_redirect and "available-at" in variant:
            child = urllib.parse.urljoin(
                module_url_value, variant["available-at"]["url"]
            )
            with urllib.request.urlopen(child, timeout=60) as response:
                child_module = json.load(response)
            found = find_klib(child_module, child, follow_redirect=False)
            if found:
                return found
    return None


def read_manifest(klib_url: str) -> tuple[str, str] | None:
    with urllib.request.urlopen(klib_url, timeout=60) as response:
        data = response.read()
    with zipfile.ZipFile(io.BytesIO(data)) as archive:
        names = [
            name for name in archive.namelist()
            if name.lower().endswith("manifest")
        ]
        if not names:
            return None
        text = archive.read(names[0]).decode("utf-8", errors="replace")
    abi = re.search(r"^abi_version=(.+)$", text, re.MULTILINE)
    compiler = re.search(r"^compiler_version=(.+)$", text, re.MULTILINE)
    if not abi or not compiler:
        return None
    return abi.group(1).strip(), compiler.group(1).strip()


def main() -> int:
    failed = False
    for group, artifact, version in DEPS:
        name = f"{group}:{artifact}:{version}"
        try:
            url = module_url(group, artifact, version)
            with urllib.request.urlopen(url, timeout=60) as response:
                module = json.load(response)
            klib = find_klib(module, url)
            if klib is None:
                print(f"{name}: no iosSimulatorArm64 klib variant")
                continue
            manifest = read_manifest(klib)
            if manifest is None:
                print(f"{name}: no ABI manifest in {klib}")
                continue
            abi, compiler = manifest
            try:
                parts = tuple(map(int, abi.split(".")))
                compatible = parts[:2] <= (2, 3)
            except ValueError:
                compatible = False
            status = "OK" if compatible else "INCOMPATIBLE"
            print(f"{status:12} {name}: abi={abi} compiler={compiler}")
            failed |= not compatible
        except Exception as exc:  # noqa: BLE001 - diagnostic tool
            print(f"ERROR        {name}: {exc}")
            failed = True
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
