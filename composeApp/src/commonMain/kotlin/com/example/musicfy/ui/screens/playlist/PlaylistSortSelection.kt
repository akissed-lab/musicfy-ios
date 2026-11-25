package com.example.musicfy.ui.screens.playlist

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import com.example.musicfy.constants.PlaylistSortType
import com.example.musicfy.viewmodels.PlaylistsViewModel

/**
 * Sort handlers matching upstream PR #368.
 *
 * [selectSortType] is the upstream wrapper: choosing CREATE_DATE resets the direction to
 * descending (newest first). [chooseOption] is for the sort dialog and only calls it for a
 * *different* type, so tapping the already-selected option does not implicitly flip direction;
 * use [toggleDirection] for that instead.
 */
data class PlaylistSortActions(
    val selectSortType: (PlaylistSortType) -> Unit,
    val toggleDirection: () -> Unit,
    val chooseOption: (PlaylistSortType) -> Unit,
)

@Composable
fun rememberPlaylistSortActions(
    current: PlaylistSortType,
    descending: Boolean,
    onSortTypeChange: (PlaylistSortType) -> Unit,
    onDescendingChange: (Boolean) -> Unit,
): PlaylistSortActions = remember(current, descending, onSortTypeChange, onDescendingChange) {
    val selectSortType: (PlaylistSortType) -> Unit = { selected ->
        onSortTypeChange(selected)
        if (selected == PlaylistSortType.CREATE_DATE) onDescendingChange(true)
    }
    PlaylistSortActions(
        selectSortType = selectSortType,
        toggleDirection = { onDescendingChange(!descending) },
        chooseOption = { selected ->
            if (selected != current) selectSortType(selected)
        },
    )
}

/** Convenience overload wired directly to [PlaylistsViewModel]'s persisted sort preferences. */
@Composable
fun rememberPlaylistSortActions(viewModel: PlaylistsViewModel): PlaylistSortActions {
    val current by viewModel.selectedSortType.collectAsState()
    val descending by viewModel.sortDescending.collectAsState()
    return rememberPlaylistSortActions(
        current = current,
        descending = descending,
        onSortTypeChange = viewModel::selectSortType,
        onDescendingChange = viewModel::setSortDescending,
    )
}
