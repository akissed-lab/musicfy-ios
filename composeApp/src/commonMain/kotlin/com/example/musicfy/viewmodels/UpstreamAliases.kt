package com.example.musicfy.viewmodels

import com.example.musicfy.db.MusicDatabaseFacade

/**
 * Compatibility alias for code ported verbatim from upstream, where the Room class was
 * named `MusicDatabase`. The ported KMP facade deliberately has a different real name
 * (SQLDelight generates `com.example.musicfy.db.MusicDatabase`), so keep the alias in
 * this package only; don't import both into the same file.
 */
typealias MusicDatabase = MusicDatabaseFacade
