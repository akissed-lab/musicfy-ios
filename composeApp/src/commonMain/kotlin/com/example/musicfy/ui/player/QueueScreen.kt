package com.example.musicfy.ui.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.musicfy.LocalPlayerUiState
import com.example.musicfy.player.LocalPlaybackController
import com.example.musicfy.player.model.QueueItemData
import com.example.musicfy.resources.Res
import com.example.musicfy.resources.close
import com.example.musicfy.resources.delete
import com.example.musicfy.resources.drag_handle
import com.example.musicfy.resources.favorite
import com.example.musicfy.resources.favorite_border
import com.example.musicfy.resources.music_note
import com.example.musicfy.resources.n_song
import com.example.musicfy.resources.next_in_queue
import com.example.musicfy.resources.pause
import com.example.musicfy.resources.play
import com.example.musicfy.resources.queue
import com.example.musicfy.resources.queue_all_songs
import com.example.musicfy.resources.queue_music
import com.example.musicfy.resources.remove_from_history
import com.example.musicfy.ui.component.GlassCard
import com.example.musicfy.ui.component.GlassStyle
import com.example.musicfy.ui.component.GlassTokens
import com.example.musicfy.ui.component.ListItem
import com.example.musicfy.ui.component.ListThumbnailSize
import com.example.musicfy.ui.component.LocalGlassState
import com.example.musicfy.ui.component.PlaylistThumbnail
import com.example.musicfy.ui.component.ThumbnailCornerRadius
import com.example.musicfy.ui.component.joinByBullet
import com.example.musicfy.ui.component.makeTimeString
import com.example.musicfy.ui.player.models.CardMorphState
import com.example.musicfy.ui.player.models.LikeBurstState
import com.example.musicfy.ui.player.models.PlayerLikeSlot
import com.example.musicfy.ui.player.styles.PlayerStyleKit
import com.example.musicfy.ui.utils.stableSystemBars
import kotlinx.coroutines.flow.distinctUntilChanged
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

private enum class QueueSection {
    HISTORY,
    UPCOMING,
}

private sealed interface QueueRow {
    data class Header(val section: QueueSection, val count: Int) : QueueRow

    data class Song(
        val item: QueueItemData,
        val section: QueueSection,
        val isCurrent: Boolean,
    ) : QueueRow

    data object Empty : QueueRow
}

/**
 * The expanded player's queue page.
 *
 * Sections, reordering and queue operations are kept; shared-element flights and the Android-only
 * haptics/shader work are simplified to the common glass primitives.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun QueueScreen(
    onClose: () -> Unit,
    contentBottomInset: Dp,
    controlsClearance: Dp,
    likeBurst: LikeBurstState? = null,
    enterProgressProvider: () -> Float = { 1f },
    morph: CardMorphState? = null,
    onImmersiveChange: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val uiState = LocalPlayerUiState.current ?: return
    val controller = LocalPlaybackController.current ?: return

    val snapshot by uiState.queueSnapshot.collectAsState()
    val track by uiState.trackInfo.collectAsState()
    val transport by controller.transportState.collectAsState()
    val accent = LocalGlassState.current.accent
    val insets = WindowInsets.stableSystemBars.asPaddingValues()

    val rows = remember(snapshot.items, snapshot.currentIndex) {
        buildQueueRows(snapshot.items, snapshot.currentIndex)
    }
    val listState = rememberLazyListState()

    var lastScroll by remember { mutableIntStateOf(0) }
    var immersive by remember { mutableStateOf(false) }
    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemScrollOffset }
            .distinctUntilChanged()
            .collect { offset ->
                val down = offset > lastScroll
                if (down != immersive) {
                    immersive = down
                    onImmersiveChange(down)
                }
                lastScroll = offset
            }
    }
    LaunchedEffect(morph) {
        morph?.setProgress(enterProgressProvider())
    }

    LazyColumn(
        state = listState,
        modifier = modifier
            .fillMaxSize()
            .padding(top = insets.calculateTopPadding()),
    ) {
        item(key = "queue-header") {
            GlassCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                style = GlassStyle(cornerRadius = 22.dp, elevation = 10.dp),
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    IconButton(onClick = onClose) {
                        Icon(
                            painter = painterResource(Res.drawable.close),
                            contentDescription = stringResource(Res.string.close),
                            tint = accent,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = snapshot.title.ifBlank { stringResource(Res.string.queue) },
                            style = PlayerStyleKit.textStyle(19),
                            color = androidx.compose.ui.graphics.Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            text = pluralStringResource(Res.plurals.n_song, snapshot.items.size, snapshot.items.size),
                            style = PlayerStyleKit.textStyle(11),
                            color = GlassTokens.MutedContent,
                        )
                    }
                }
            }
        }

        itemsIndexed(
            items = rows,
            key = { _, row ->
                when (row) {
                    is QueueRow.Header -> "header-${row.section}"
                    is QueueRow.Song -> "song-${row.item.uid}"
                    QueueRow.Empty -> "empty"
                }
            },
        ) { index, row ->
            when (row) {
                is QueueRow.Header -> QueueSectionHeader(
                    section = row.section,
                    count = row.count,
                    historyUids = remember(rows) {
                        rows.mapNotNull { (it as? QueueRow.Song)?.takeIf { song -> song.section == QueueSection.HISTORY }?.item?.uid }
                    },
                    onClearHistory = { uids -> controller.removeQueueItems(uids) },
                    modifier = Modifier.fillMaxWidth(),
                )

                is QueueRow.Song -> QueueSongRow(
                    row = row,
                    isPlaying = row.isCurrent && transport.isPlaying,
                    liked = track.liked,
                    accent = accent,
                    canMoveUp = precedingUid(rows, index, row.item.uid) != null,
                    onClick = { controller.playQueueItem(row.item.uid) },
                    onLike = {
                        controller.toggleLike()
                        if (!track.liked) likeBurst?.burst(PlayerLikeSlot.QUEUE)
                    },
                    onPlayNext = { controller.playQueueItemNext(row.item.uid) },
                    onMoveUp = {
                        precedingUid(rows, index, row.item.uid)?.let { after ->
                            controller.moveQueueItem(row.item.uid, after)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )

                QueueRow.Empty -> Text(
                    text = stringResource(Res.string.queue_all_songs),
                    style = PlayerStyleKit.textStyle(13),
                    color = GlassTokens.MutedContent,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                )
            }
        }

        item(key = "queue-tail") {
            Spacer(
                Modifier
                    .fillMaxWidth()
                    .height(contentBottomInset + controlsClearance + 24.dp),
            )
        }
    }
}

@Composable
private fun QueueSectionHeader(
    section: QueueSection,
    count: Int,
    historyUids: List<Int>,
    onClearHistory: (List<Int>) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(horizontal = 20.dp, vertical = 10.dp),
    ) {
        Text(
            text = if (section == QueueSection.HISTORY) {
                "History"
            } else {
                stringResource(Res.string.next_in_queue)
            },
            style = PlayerStyleKit.textStyle(13),
            color = GlassTokens.MutedContent,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = pluralStringResource(Res.plurals.n_song, count, count),
            style = PlayerStyleKit.textStyle(11),
            color = GlassTokens.MutedContent,
        )
        if (section == QueueSection.HISTORY && historyUids.isNotEmpty()) {
            IconButton(onClick = { onClearHistory(historyUids) }) {
                Icon(
                    painter = painterResource(Res.drawable.delete),
                    contentDescription = stringResource(Res.string.remove_from_history),
                    tint = GlassTokens.MutedContent,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
    }
}

@Composable
private fun QueueSongRow(
    row: QueueRow.Song,
    isPlaying: Boolean,
    liked: Boolean,
    accent: androidx.compose.ui.graphics.Color,
    canMoveUp: Boolean,
    onClick: () -> Unit,
    onLike: () -> Unit,
    onPlayNext: () -> Unit,
    onMoveUp: () -> Unit,
    modifier: Modifier = Modifier,
) {
    ListItem(
        modifier = modifier.clickable { onClick() },
        title = row.item.title,
        subtitle = joinByBullet(row.item.artist, makeTimeString(row.item.duration)),
        thumbnailContent = {
            PlaylistThumbnail(
                thumbnails = listOfNotNull(row.item.artworkUri),
                size = ListThumbnailSize,
                placeHolder = {
                    Icon(
                        painter = painterResource(Res.drawable.music_note),
                        contentDescription = null,
                        tint = GlassTokens.MutedContent,
                        modifier = Modifier.fillMaxSize(),
                    )
                },
                shape = RoundedCornerShape(ThumbnailCornerRadius),
            )
        },
        trailingContent = {
            if (row.isCurrent) {
                Icon(
                    painter = painterResource(if (isPlaying) Res.drawable.pause else Res.drawable.play),
                    contentDescription = null,
                    tint = accent,
                    modifier = Modifier.size(18.dp),
                )
                Spacer(Modifier.size(4.dp))
            }
            IconButton(onClick = onLike) {
                Icon(
                    painter = painterResource(if (liked) Res.drawable.favorite else Res.drawable.favorite_border),
                    contentDescription = null,
                    tint = if (liked) GlassTokens.Pink else GlassTokens.MutedContent,
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = onPlayNext) {
                Icon(
                    painter = painterResource(Res.drawable.queue_music),
                    contentDescription = "Play next",
                    tint = GlassTokens.MutedContent,
                    modifier = Modifier.size(18.dp),
                )
            }
            IconButton(onClick = onMoveUp, enabled = canMoveUp) {
                Icon(
                    painter = painterResource(Res.drawable.drag_handle),
                    contentDescription = "Move up",
                    tint = if (canMoveUp) GlassTokens.MutedContent else GlassTokens.Stroke,
                    modifier = Modifier.size(18.dp),
                )
            }
        },
        isActive = row.isCurrent,
        isSelected = row.isCurrent,
    )
}

private fun buildQueueRows(
    items: List<QueueItemData>,
    currentIndex: Int,
): List<QueueRow> {
    if (items.isEmpty()) return listOf(QueueRow.Empty)

    val history = mutableListOf<QueueRow.Song>()
    val upcoming = mutableListOf<QueueRow.Song>()
    items.forEachIndexed { index, item ->
        if (index < currentIndex) {
            history += QueueRow.Song(item, QueueSection.HISTORY, false)
        } else {
            upcoming += QueueRow.Song(item, QueueSection.UPCOMING, index == currentIndex)
        }
    }

    return buildList {
        if (history.isNotEmpty()) {
            add(QueueRow.Header(QueueSection.HISTORY, history.size))
            addAll(history)
        }
        if (upcoming.isNotEmpty()) {
            add(QueueRow.Header(QueueSection.UPCOMING, upcoming.size))
            addAll(upcoming)
        }
    }
}

/** The uid to move a row after when its handle is tapped. */
private fun precedingUid(
    rows: List<QueueRow>,
    index: Int,
    currentUid: Int,
): Int? {
    for (i in index - 1 downTo 0) {
        val row = rows[i] as? QueueRow.Song ?: continue
        if (row.item.uid != currentUid && row.section == QueueSection.UPCOMING) return row.item.uid
    }
    return null
}
