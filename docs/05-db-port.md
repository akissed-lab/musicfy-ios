# Database port plan (Room → SQLDelight)

Upstream `MusicDatabase` is a facade: `DatabaseDao by delegate` plus a separate
`SpeedDialDao` and a bounded `pinToSpeedDial(item)` helper. SQLDelight already
generates `interface MusicDatabase`, so the ported facade will be named
`MusicDatabaseFacade` to avoid a package-level collision.

## Schema fixes required before writing the facade

- `lyrics.translationMode`: `TEXT NOT NULL DEFAULT ''` (upstream stores a
  `String`; the current `INTEGER` column would map to `Long`).
- Timestamp columns are epoch milliseconds (`INTEGER`). Map with UTC on both
  read and write, matching upstream `Converters`; `nowLocal()` must use
  `TimeZone.UTC` when it feeds DB fields.
- SQLDelight generates `Long` for Room `Int`/`Boolean` columns. Mappers convert
  with `.toInt()` / `== 1L`; inserts use `.toLong()` / `1L or 0L`.
- `song.date` was changed from `TEXT` to `INTEGER`; `romanizeLyrics` default
  changed from `0` to `1` to match Room.
- Quote `song_album_map.\`index\``, `album_artist_map.\`order\`` and
  `search_history.\`query\`` in every new query.
- `playlist_song_map.id` is generated as `Long`; map to `Int` explicitly.

## Relation assembly

Room `@Relation` results are rebuilt in Kotlin:

1. `songArtists(songId)` — artists joined through `song_artist_map`, ordered by
   `position`.
2. `songAlbum(songId)` — album joined through `song_album_map`, ordered by
   `index`, first row.
3. `albumArtists(albumId)` — through `album_artist_map`, ordered by `order`.
4. `format(id)` — direct row.
5. For lists, batch-artist/album lookups once and cache per song ID to avoid
   N+1 queries.

Never use SQLDelight's generated flat `Song`/`Album`/`Artist` row types where
the UI expects `db.entities.*` relation models.

## First facade operations

Ordered by upstream call-site usage:

1. `query {}` / `transaction {}` / `withTransaction {}` wrappers over
   `MusicQueries` + `SqlDriver`.
2. `song(id)`, `getSongById`, `getSongsByIds`, `songsByIds`.
3. `songs(sortType, descending)`, `likedSongs(...)`, `mostPlayedSongs(...)`,
   `recentlyPlayedSongs(limit)`.
4. `album(id)`, `albums(...)`, `albumWithSongs(id)`, `albumSongs(id)`.
5. `artist(id)`, `artists(...)`, `artistSongs(...)`, `mostPlayedArtists(...)`.
6. `playlist(id)`, `playlists(...)`, `playlistSongs(id)`, playlist writes and
   reorder operations.
7. `searchSongs`, `searchArtists`, `searchAlbums`, `searchPlaylists`.
8. `format(id)`, `lyrics(id)`, set-video-id lookups, lyric-offset writes.
9. `events()`, `recentEvents`, `eventCount`, `firstEventTimestamp`,
   `recapEvents`.
10. `SpeedDialDao` parity: `getAll`, `oldest`, `count`, `insert`, `delete`,
    `isPinned`, capped `pinToSpeedDial`.

Room `Flow<T>` queries become `Query<T>.asFlow()`. This requires adding
`app.cash.sqldelight:coroutines-extensions` (same version as the runtime) to the
version catalog and `composeApp` dependencies.

## Insert/update parity

Use `INSERT OR IGNORE` for upstream `@Insert(OnConflictStrategy.IGNORE)` and
explicit `UPDATE` for `@Update`. Avoid `INSERT OR REPLACE` for songs: `REPLACE`
deletes and reinserts, which fires foreign-key cascades and drops relation rows
that upstream keeps.
