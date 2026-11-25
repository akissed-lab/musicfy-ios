// ModuleExamplePages.kt
//
// Small, self-contained examples of the three custom pages from upstream PR
// #407. They read only data passed by the host (track query, queue snapshot,
// playback position) and draw with the shared glass primitives, so they compile
// on iOS without Android views, file access or dynamic module code.

package com.example.musicfy.ui.screens.modules

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.example.musicfy.modules.ModuleCapability
import com.example.musicfy.modules.ModuleDescriptor
import com.example.musicfy.modules.ModuleLyricLine
import com.example.musicfy.modules.ModuleLyrics
import com.example.musicfy.modules.ModuleQueueEntry
import com.example.musicfy.modules.ModuleQueueInput
import com.example.musicfy.modules.ModuleQueueView
import com.example.musicfy.modules.ModuleRegistration
import com.example.musicfy.modules.ModuleRegistry
import com.example.musicfy.modules.ModuleSheet
import com.example.musicfy.modules.ModuleSongInfo
import com.example.musicfy.modules.ModuleTrackQuery
import com.example.musicfy.player.model.QueueItemData
import com.example.musicfy.player.model.QueueState
import com.example.musicfy.ui.component.GlassCard
import com.example.musicfy.ui.component.GlassStyle
import com.example.musicfy.ui.component.GlassTokens

private const val ExampleModuleId = "example.pages"

/** Custom lyrics page, including the word-by-word sweep when enhanced stamps exist. */
@Composable
fun ModuleLyricsPage(
    query: ModuleTrackQuery,
    positionMs: Long = 0L,
    moduleId: String? = null,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val registrations by ModuleRegistry.entries.collectAsState()
    val provider = remember(moduleId, registrations) { ModuleRegistry.lyricsProvider(moduleId) }
    var lyrics by remember { mutableStateOf<ModuleLyrics?>(null) }
    LaunchedEffect(provider, query) {
        lyrics = runCatching { provider?.lyrics(query) }.getOrNull()
    }

    PageScaffold(title = "Lyrics · ${query.title}", onClose = onClose, modifier = modifier) {
        val current = lyrics
        if (current == null || current.lines.isEmpty()) {
            Text(
                text = "No module lyrics for this song.",
                style = MaterialTheme.typography.bodyMedium,
                color = GlassTokens.MutedContent,
            )
        } else {
            val activeLine = current.activeIndex(positionMs)
            current.lines.forEachIndexed { index, line ->
                val highlighted = index == activeLine
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    style = GlassStyle(
                        containerColor = if (highlighted) Color(0x33FA4C7E) else GlassStyle().containerColor,
                    ),
                ) {
                    LyricLineText(line = line, highlighted = highlighted, positionMs = positionMs)
                    line.translation?.let { translation ->
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = translation,
                            style = MaterialTheme.typography.bodySmall,
                            color = GlassTokens.MutedContent,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LyricLineText(
    line: ModuleLyricLine,
    highlighted: Boolean,
    positionMs: Long,
) {
    if (line.words.isEmpty()) {
        Text(
            text = line.text,
            style = MaterialTheme.typography.bodyLarge,
            color = if (highlighted) Color.White else GlassTokens.MutedContent,
        )
        return
    }
    val activeWord = if (highlighted) line.activeWordIndex(positionMs) else -1
    val annotated = buildAnnotatedString {
        line.words.forEachIndexed { index, word ->
            val sung = index == activeWord
            withStyle(
                SpanStyle(
                    color = if (sung) GlassTokens.Pink else GlassTokens.MutedContent,
                    fontWeight = if (sung) FontWeight.Bold else FontWeight.Normal,
                ),
            ) {
                append(word.text)
            }
            if (index != line.words.lastIndex) append(" ")
        }
    }
    Text(text = annotated, style = MaterialTheme.typography.bodyLarge)
}

/** Custom queue page over a snapshot the host owns; tapping a row asks the host to play it. */
@Composable
fun ModuleQueuePage(
    queue: QueueState,
    onPick: (QueueItemData) -> Unit,
    onClose: () -> Unit,
    moduleId: String? = null,
    modifier: Modifier = Modifier,
) {
    val registrations by ModuleRegistry.entries.collectAsState()
    val provider = remember(moduleId, registrations) { ModuleRegistry.queueProvider(moduleId) }
    val input = remember(queue) {
        ModuleQueueInput(
            title = queue.title,
            entries = queue.items.mapIndexed { index, item ->
                ModuleQueueEntry(
                    uid = item.uid,
                    mediaId = item.mediaId,
                    title = item.title,
                    artist = item.artist,
                    artworkUri = item.artworkUri,
                    durationMs = item.duration,
                    isCurrent = index == queue.currentIndex,
                )
            },
        )
    }
    val view = remember(provider, input) {
        provider?.queue(input) ?: ModuleQueueView(title = input.title, entries = input.entries)
    }

    PageScaffold(title = "Queue · ${view.title}", onClose = onClose, modifier = modifier) {
        if (view.entries.isEmpty()) {
            Text(
                text = view.emptyMessage ?: "Nothing queued right now.",
                style = MaterialTheme.typography.bodyMedium,
                color = GlassTokens.MutedContent,
            )
        }
        view.entries.forEach { entry ->
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { queue.items.firstOrNull { it.uid == entry.uid }?.let(onPick) },
                style = GlassStyle(
                    containerColor = if (entry.isCurrent) Color(0x337B5CFF) else GlassStyle().containerColor,
                ),
            ) {
                Text(
                    text = entry.title.ifBlank { entry.mediaId },
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                )
                Text(
                    text = entry.artist,
                    style = MaterialTheme.typography.bodySmall,
                    color = GlassTokens.MutedContent,
                )
            }
        }
    }
}

/** Custom song-info page built from module contributions and host-owned metadata. */
@Composable
fun ModuleSongInfoPage(
    query: ModuleTrackQuery,
    onClose: () -> Unit,
    moduleId: String? = null,
    modifier: Modifier = Modifier,
) {
    val registrations by ModuleRegistry.entries.collectAsState()
    val contributors = remember(moduleId, registrations) { ModuleRegistry.songInfoContributors(moduleId) }
    var facts by remember { mutableStateOf<List<ModuleSongInfo>>(emptyList()) }
    LaunchedEffect(contributors, query) {
        facts = contributors
            .mapNotNull { contributor -> runCatching { contributor.songInfo(query) }.getOrNull() }
            .filterNot { it.isEmpty }
    }

    PageScaffold(title = "Song info · ${query.title}", onClose = onClose, modifier = modifier) {
        if (facts.isEmpty()) {
            Text(
                text = "No module knows more about this song on iOS.",
                style = MaterialTheme.typography.bodyMedium,
                color = GlassTokens.MutedContent,
            )
        }
        facts.forEach { info ->
            GlassCard(modifier = Modifier.fillMaxWidth()) {
                InfoRow("Album", info.album)
                InfoRow("Released", info.releaseDate)
                InfoRow("Genres", info.genres.joinToString(", ").ifBlank { null })
                InfoRow("Writers", info.writers.joinToString(", ").ifBlank { null })
                InfoRow("Producers", info.producers.joinToString(", ").ifBlank { null })
                InfoRow("Label", info.label)
                InfoRow("About", info.about)
            }
        }
    }
}

/** Registers the example sheets. Call once from app startup; no app files are edited here. */
fun registerExampleModuleSheets() {
    ModuleRegistry.register(
        ModuleRegistration(
            descriptor = ModuleDescriptor(
                id = ExampleModuleId,
                name = "Example module pages",
                description = "Compose-only sheet examples for lyrics, queue and song info.",
                capabilities = setOf(ModuleCapability.Sheets),
            ),
            sheets = listOf(
                ModuleSheet(id = "lyrics", title = "Lyrics page", heightFraction = 0.7f) {
                    Text(
                        text = "Open ModuleLyricsPage with the current track query and playback position.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                    )
                },
                ModuleSheet(id = "queue", title = "Queue page", heightFraction = 0.65f) {
                    Text(
                        text = "Open ModuleQueuePage with the host's QueueState and a play callback.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                    )
                },
                ModuleSheet(id = "songinfo", title = "Song info page", heightFraction = 0.65f) {
                    Text(
                        text = "Open ModuleSongInfoPage with the current ModuleTrackQuery.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White,
                    )
                },
            ),
        ),
    )
}

/** Convenience for a host button that wants to show one of the example sheets. */
fun openExampleSheet(sheetId: String) {
    ModuleSheetRequests.open(ExampleModuleId, sheetId)
}

@Composable
private fun InfoRow(label: String, value: String?) {
    if (value.isNullOrBlank()) return
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = GlassTokens.MutedContent,
            modifier = Modifier.width(88.dp),
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun PageScaffold(
    title: String,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                color = Color.White,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "Close",
                style = MaterialTheme.typography.labelLarge,
                color = GlassTokens.Pink,
                modifier = Modifier.clickable(onClick = onClose),
            )
        }
        Spacer(Modifier.height(16.dp))
        content()
    }
}
