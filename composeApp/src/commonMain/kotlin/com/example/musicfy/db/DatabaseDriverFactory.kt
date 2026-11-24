package com.example.musicfy.db

import app.cash.sqldelight.db.SqlDriver

/** Platform-specific SQLite driver factory (iOS implementation in iosMain). */
expect class DatabaseDriverFactory() {
    fun createDriver(): SqlDriver
}

/**
 * Room `InternalDatabase` equivalent. Generated queries and models come from
 * `commonMain/sqldelight/com/example/musicfy/db/Music.sq`.
 *
 * Android used schema version 41 with selected hand-written migrations and
 * fallbackToDestructiveMigration. SQLDelight manages migrations explicitly;
 * `PRAGMA user_version` must be set to 41 on first open to keep the export
 * manifest compatible (`databaseVersion: 41`).
 */
const val DATABASE_VERSION = 41
const val DATABASE_FILE_NAME = "song.db"
