package com.example.musicfy.ui.player.models

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

/** The three pages the expanded player can show. */
enum class PlayerPage {
    PLAYER,
    LYRICS,
    QUEUE,
}

/** Local replacement for the upstream `studio.PlayerMenuAction` (that package is not owned here). */
enum class PlayerAction {
    LIKE,
    LIBRARY,
    REPEAT,
    SHUFFLE,
    SLEEP_TIMER,
    SPEED,
    LYRICS,
    QUEUE,
    STUDIO,
    MORE,
}

/** Where a like animation should land. */
enum class PlayerLikeSlot {
    PLAYER,
    LYRICS,
    QUEUE,
}

/**
 * Minimal common replacement for the Android-only sheet/card-morph states.
 *
 * It deliberately owns only the values the ported UI reads: whether the sheet is expanded, its
 * visual progress, and which page is open. The real gesture system lives in [PlayerHandle] and the
 * shared popup host; nothing here depends on Android views or Media3.
 */
@Stable
class PlayerSheetState(initiallyExpanded: Boolean = true) {
    var expanded: Boolean by mutableStateOf(initiallyExpanded)
        private set

    var progress: Float by mutableFloatStateOf(if (initiallyExpanded) 1f else 0f)
        private set

    var page: PlayerPage by mutableStateOf(PlayerPage.PLAYER)
        private set

    val isExpandedOrExpanding: Boolean
        get() = expanded || progress > 0.5f

    fun expand() {
        expanded = true
        progress = 1f
    }

    fun collapse() {
        expanded = false
        progress = 0f
    }

    fun toggle() {
        if (expanded) collapse() else expand()
    }

    fun openPage(page: PlayerPage) {
        this.page = page
        expand()
    }

    fun closePage() {
        page = PlayerPage.PLAYER
    }
}

@Composable
fun rememberPlayerSheetState(initiallyExpanded: Boolean = true): PlayerSheetState =
    remember { PlayerSheetState(initiallyExpanded) }

/** The card's share of the page-open transition, read while drawing. */
@Stable
class CardMorphState {
    var progress: Float by mutableFloatStateOf(1f)
        private set

    fun setProgress(value: Float) {
        progress = value.coerceIn(0f, 1f)
    }
}

@Composable
fun rememberCardMorphState(): CardMorphState = remember { CardMorphState() }

/** Tiny trigger counter: composables only need to know that a like happened. */
@Stable
class LikeBurstState {
    var slot: PlayerLikeSlot by mutableStateOf(PlayerLikeSlot.PLAYER)
        private set

    var trigger: Int by mutableIntStateOf(0)
        private set

    fun burst(slot: PlayerLikeSlot = this.slot) {
        this.slot = slot
        trigger++
    }
}

@Composable
fun rememberLikeBurstState(): LikeBurstState = remember { LikeBurstState() }
