# Upstream and reference policy

This document is the single place for provenance metadata. It supersedes the
older "Musicfy 8.1.2 / `db2b3d1`" framing in earlier notes; those numbers are
kept only where they explain the legacy snapshot.

## Source of truth

| Field | Value |
| --- | --- |
| Upstream | [`realidkroo/Mochy-Music`](https://github.com/realidkroo/Mochy-Music) |
| Branch | `main` |
| Pinned HEAD | `e6a17d2eb9f48b503fe07e35049cb522b8486db2` |
| Release | `mochy-8.5.0.0` RC1 (product version 8.5.0.0) |
| License | GPL-3.0 (root `LICENSE` is inherited from upstream) |

The iOS port targets the tree above. Kotlin package names and transfer-format
identifiers remain `com.example.musicfy` for compatibility; the *product* name
in documentation is **Mochy Music**.

## The rename and the old baseline

- The former upstream was `realidkroo/musicfy`. Its `musicfy-8.1.2` release tag
  (`b73b4a3`) contained only a farewell README and two APKs; the real Android
  sources lived on `main` at `db2b3d1` (8.1.2 source).
- The old tree already contained the "Move to Mochy" bridge
  (`MoveToMochyViewModel.kt`, `com.mochyy.mochymusic` package IDs). That
  transition is complete: the maintained repository is now `Mochy-Music`.
- Between the legacy `db2b3d1` baseline and the pinned `e6a17d2` HEAD there are
  **646 commits and roughly 300 changed files**. Therefore file counts, line
  counts, settings-key counts and string counts inherited from `db2b3d1` are
  historical measurements, not 8.5 facts.

## `reference/` policy

`reference/` is a **local, non-compiled snapshot** used for side-by-side
porting. It is not the source of truth and is never fetched during checks.

1. The snapshot currently checked into this repo is still the legacy
   `musicfy` `db2b3d1` tree (837 Kotlin files, ~186k lines). Do not describe
   it as 8.5 source.
2. `tools/verify.py` counts `reference/**/*.kt` and `composeApp/src/**/*.kt`.
   Its percentage is relative to whatever snapshot is present, so today's
   denominator is the legacy baseline.
3. To rebaseline: replace `reference/` with the tree from `Mochy-Music` `main`
   at `e6a17d2…`, regenerate derived artifacts (`SettingsKeys.kt` via
   `tools/gen_settings_keys.py`, strings via `tools/copy_strings.py` after
   repointing its local `UPSTREAM` path), then run `python tools/verify.py`.
   No Gradle is involved in that recount.
4. After rebaselining, update the legacy-qualified numbers in
   `docs/00-overview.md`, `docs/01-architecture.md`, `docs/02-roadmap.md`,
   `docs/03-providers.md` and `docs/09-work-packages.md`, plus the generated
   `docs/04-port-status.md`.

## Tool behavior

| Tool | Reads | Notes |
| --- | --- | --- |
| `tools/verify.py` | `reference/`, `composeApp/src/` | Pure Python; no network, no Gradle, no Xcode. Reports brace-balance and unsupported-import warnings. Counting logic is snapshot-relative and intentionally unchanged by the rename. |
| `tools/find_portable.py` | `reference/` | Lists snapshot files with no Android-only imports; snapshot-relative. |
| `tools/gen_settings_keys.py` | `reference/.../PreferenceKeys.kt` | Regenerates `SettingsKeys.kt` from whatever snapshot is present. |
| `tools/copy_strings.py` | Hardcoded local temp clone of the **old** upstream | Must be repointed at the new checkout before use; left unchanged here to avoid breaking the script. |
| `tools/check_klib_abi.py` | Maven Central only | Dependency ABI check; unrelated to upstream provenance. |

## Scope of wave 9

`docs/09-work-packages.md` lists the ten portable wave 9 packages (settings
search, custom fonts, es localization, official-video lookup, playlist sort,
transitions, audio FX mixer, module surfaces, word-level lyrics, song info).
It also marks the Android-only items that are explicitly **not ported**: C++
DSP / Liveprog / EEL2, USB DAC / `libusb`, AGSL / GPU fallback, Cast / DLNA,
module sandbox / JS, and web docs.
