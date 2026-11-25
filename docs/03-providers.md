# Provider layer mapping

Source of truth: `providers/*` and `modules/{api,applemusic,monochrome}` in
[`realidkroo/Mochy-Music`](https://github.com/realidkroo/Mochy-Music), branch
`main` @ `e6a17d2eb9f48b503fe07e35049cb522b8486db2` (release `mochy-8.5.0.0`
RC1). The local `reference/` tree is still the legacy `musicfy` `db2b3d1`
snapshot; module/provider counts from it are baseline-only. Target:
`composeApp/src/commonMain/kotlin` in the KMP app — the Kotlin/Compose code is
the port; Swift is only the Xcode entry point.

Android used OkHttp/Ktor + kotlinx.serialization; the port keeps Ktor with the
Darwin engine via `createPlatformHttpClient()` and kotlinx.serialization.
Query cascades, matching tolerances, LRC formats and rate limits stay as in
upstream.

| Android module | Status in port | Notes |
|---|---|---|
| `providers/innertube` | `[~]` lyrics slice done | `YouTube.transcript()`, `captions()`, `next()`/`lyrics()` and Innertube request/response models ported. Search/library/playback endpoints and po-token/visitor-data proxy remain. |
| `providers/canvas` | `[ ]` | Pending. |
| `providers/applecanvas` | `[ ]` | Pending, depends on canvas. |
| `providers/artistvideo` | `[ ]` | Pending. |
| `providers/lrclib` | `[x]` | 5-step search cascade and ranking preserved. |
| `providers/kugou` | `[x]` | Base64 LRC decoding preserved. |
| `providers/betterlyrics` | `[x]` | TTML→LRC converter and voice tags preserved. |
| `providers/simpmusic` | `[x]` | Fallback host and duration tolerance preserved. |
| `providers/youlyplus` | `[x]` | Two-mirror race redone with coroutines. |
| `providers/paxsenixlyrics` | `[x]` | Parallel requests via coroutines. |
| `providers/directlyrics` | `[x]` | Deezer/NetEase/QQ clients ported. |
| `providers/lastfm` | `[ ]` | `api_sig` secret must live on a proxy. |
| `providers/shazamkit` | `[ ]` | Native ShazamKit bridge pending. |
| `modules/api` | `[ ]` | `StreamDrm.Widevine` needs a FairPlay replacement. |
| `modules/applemusic` | `[ ]` | MusicKit + FairPlay license proxy pending. |
| `modules/monochrome` | `[ ]` | FLAC STREAMINFO probing pending. |

## Lyrics orchestration (ported)

- `LyricsProvider` / `LyricsProviderRegistry`: all 12 upstream sources, same
  preference order and settings keys.
- `LyricsHelper`: parallel candidate fetch, cached best result, embedded tags
  first, offline mode, Chinese-service and auto-caption tiers, script/sync/duet
  grading, and `fetchCandidates()` for the source picker.
- `EmbeddedLyrics`: MP3 ID3 (USLT/SYLT/TXXX), FLAC Vorbis comments and M4A
  `©lyr`; iOS reads bytes through `NSData`.
- `LyricsUtils`: parsing, plain/rich sync, word-timing repair, script/language
  detection and playback helpers. Romanisation tables are a later stage.

## Server-side components still required for full parity

1. InnerTube client context keys, visitor data, po-tokens, signature timestamp.
2. Apple Music developer token + FairPlay license acquisition.
3. Last.fm `api_sig` signing.
4. Deezer anonymous JWT / QQ Music decryption (ToS-sensitive).

These cannot be shipped safely as hardcoded client secrets in an iOS binary.
The app will call a small proxy service; its contract is part of stage 2.
