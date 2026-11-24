package com.example.musicfy.db

import app.cash.sqldelight.driver.native.NativeSqliteDriver

actual class DatabaseDriverFactory {
    actual fun createDriver(): app.cash.sqldelight.db.SqlDriver =
        NativeSqliteDriver(MusicDatabase.Schema, DATABASE_FILE_NAME).apply {
            execute(null, "PRAGMA foreign_keys = ON", 0)
            execute(null, "PRAGMA journal_mode = WAL", 0)
            execute(null, "PRAGMA busy_timeout = 60000", 0)
            execute(null, "PRAGMA synchronous = NORMAL", 0)
            execute(null, "PRAGMA user_version = $DATABASE_VERSION", 0)
        }
}
