// ModuleBuiltIns.kt
//
// Safe, data-only modules that ship with the iOS port. Each one works entirely
// on values supplied by the host, so enabling it cannot reach the network, the
// file system or the player's internals.

package com.example.musicfy.modules

internal object ModuleBuiltIns {
    const val LyricsId = "builtin.wordlyrics"
    const val QueueId = "builtin.queueview"
    const val SongInfoId = "builtin.songfacts"
    const val SheetsId = "builtin.guide"

    val registrations: List<ModuleRegistration> = listOf(
        ModuleRegistration(
            descriptor = ModuleDescriptor(
                id = LyricsId,
                name = "Enhanced LRC words",
                description = "Turns host-provided LRC with <mm:ss.xx> stamps into word-by-word lines.",
                capabilities = setOf(ModuleCapability.Lyrics),
                builtIn = true,
            ),
            lyricsProvider = EnhancedLrcLyricsProvider,
        ),
        ModuleRegistration(
            descriptor = ModuleDescriptor(
                id = QueueId,
                name = "Queue view",
                description = "Shows upcoming songs from the host's queue snapshot.",
                capabilities = setOf(ModuleCapability.Queue),
                builtIn = true,
            ),
            queueProvider = UpcomingQueueProvider,
        ),
        ModuleRegistration(
            descriptor = ModuleDescriptor(
                id = SongInfoId,
                name = "Track facts",
                description = "Reuses the album the host already knows; no external lookups on iOS.",
                capabilities = setOf(ModuleCapability.SongInfo),
                builtIn = true,
            ),
            songInfoContributor = TrackFactsContributor,
        ),
        ModuleRegistration(
            descriptor = ModuleDescriptor(
                id = SheetsId,
                name = "Module guide",
                description = "Reserves a sheet over the player; the host supplies its content.",
                capabilities = setOf(ModuleCapability.Sheets),
                builtIn = true,
            ),
            sheets = listOf(
                ModuleSheet(
                    id = "guide",
                    title = "Modules on iOS",
                    heightFraction = 0.6f,
                ),
            ),
        ),
    )

    private object EnhancedLrcLyricsProvider : ModuleLyricsProvider {
        override val moduleId: String = LyricsId

        override suspend fun lyrics(query: ModuleTrackQuery): ModuleLyrics? = ModuleLrcParser.parse(query.rawLyrics)
    }

    private object UpcomingQueueProvider : ModuleQueueProvider {
        private const val MaxRows = 12

        override val moduleId: String = QueueId

        override fun queue(input: ModuleQueueInput): ModuleQueueView {
            val current = input.entries.indexOfFirst { it.isCurrent }
            val upcoming = if (current >= 0) {
                input.entries.drop(current + 1) + input.entries.take(current)
            } else {
                input.entries
            }
            return ModuleQueueView(
                title = input.title.ifBlank { "Up next" },
                entries = upcoming.take(MaxRows),
                emptyMessage = "Nothing queued right now.",
            )
        }
    }

    private object TrackFactsContributor : ModuleSongInfoContributor {
        override val moduleId: String = SongInfoId

        override suspend fun songInfo(query: ModuleTrackQuery): ModuleSongInfo = ModuleSongInfo(
            album = query.album,
            sourceLabel = "Track",
        )
    }
}
