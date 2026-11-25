package com.example.musicfy.db

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver

actual class DatabaseDriverFactory actual constructor() {
    actual fun createDriver(): SqlDriver =
        NativeSqliteDriver(MusicDatabase.Schema, DATABASE_FILE_NAME).apply {
            // SQLite PRAGMA setters return a result row. SQLDelight's execute() is for
            // statements without results; using it here throws
            // "Queries can be performed using SQLiteDatabase query or rawQuery methods only"
            // from SQLiter on iOS. Consume the cursor through executeQuery instead.
            execPragma("PRAGMA foreign_keys = ON")
            execPragma("PRAGMA busy_timeout = 60000")
            execPragma("PRAGMA synchronous = NORMAL")
            execPragma("PRAGMA user_version = $DATABASE_VERSION")
        }
}

private fun SqlDriver.execPragma(sql: String) {
    executeQuery<Unit>(
        identifier = null,
        sql = sql,
        mapper = { cursor ->
            while (cursor.next().value) {
                // PRAGMA setters return one row; drain it so the statement completes.
            }
            QueryResult.Unit
        },
        parameters = 0,
        binders = null,
    ).value
}
