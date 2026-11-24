// SearchScreen.kt
//
// WP5d port of the Android main Search tab. The Android original relied on
// Hilt, Activity BackHandler, SavedStateHandle, LocalPlayerAwareWindowInsets,
// YouTubeUrlParser/YouTubeQueue and the Android-only SearchDesign helpers.
// All of those are replaced here with commonMain primitives. The glass chrome
// is deliberately local (private) so this file does not depend on the shared
// SearchDesign.kt that another work package owns.
//
// TODO(search-design): swap SearchTabTopBar / SearchTabField / SearchTabAvatar
// for the shared SearchGlassTopBar / SearchField / SearchAvatar once that
// component is wired in by the integrator.

package com.example.musicfy.ui.screens.search

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.example.musicfy.LocalAppContainer
import com.example.musicfy.db.entities.SearchHistory
import com.example.musicfy.models.MediaMetadata
import com.example.musicfy.player.LocalPlaybackController
import com.example.musicfy.player.model.PlaybackTrack
import com.example.musicfy.player.model.TrackSource
import com.example.musicfy.player.queue.ListQueue
import com.example.musicfy.resources.Res
import com.example.musicfy.resources.arrow_back
import com.example.musicfy.resources.arrow_forward
import com.example.musicfy.resources.close
import com.example.musicfy.resources.history
import com.example.musicfy.resources.person
import com.example.musicfy.resources.queue_music
import com.example.musicfy.resources.search
import com.example.musicfy.resources.search_history
import com.example.musicfy.resources.suggestions
import com.example.musicfy.resources.top_result
import com.example.musicfy.resources.trending_up
import com.example.musicfy.settings.SettingsKeys
import com.example.musicfy.settings.rememberPreference
import com.example.musicfy.ui.component.BackHandler
import com.example.musicfy.ui.component.GlassCard
import com.example.musicfy.ui.component.GlassState
import com.example.musicfy.ui.component.GlassStyle
import com.example.musicfy.ui.component.GlassTokens
import com.example.musicfy.ui.component.LocalGlassState
import com.example.musicfy.ui.component.glassRoot
import com.example.musicfy.ui.component.glassSurface
import com.example.musicfy.ui.component.navigateToTab
import com.example.musicfy.ui.screens.Screens
import com.example.musicfy.ui.theme.interFontFamily
import com.example.musicfy.viewmodels.MoodAndGenresViewModel
import com.example.musicfy.viewmodels.OnlineSearchSuggestionViewModel
import com.example.musicfy.viewmodels.appViewModel
import com.example.musicfy.viewmodels.coverKey
import com.example.musicfy.viewmodels.viewModelFactory
import com.music.innertube.models.AlbumItem
import com.music.innertube.models.ArtistItem
import com.music.innertube.models.PlaylistItem
import com.music.innertube.models.SongItem
import com.music.innertube.models.YTItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/** set on the search page's entry to open its field with the keyboard up */
private const val SearchFocusKey = "focusSearch"

/**
 * Common navigation has no Android `Bundle`/`SavedStateHandle` flag to flag a
 * tab entry with, so double-tapping the Search tab writes to this small signal
 * instead. TODO(nav): restore the savedStateHandle path if common navigation
 * gains per-entry state parity.
 */
private object SearchTabFocusSignal {
    val requested = MutableStateFlow(false)
}

/** opens the search page's field, ready to type (the Search tab's double tap) */
fun NavController.requestSearchFocus() {
    // The controller is intentionally unused until common navigation exposes
    // NavBackStackEntry.savedStateHandle the way Android does. See SearchFocusKey.
    SearchTabFocusSignal.requested.value = true
}

/** Local stand-in for the parallel OnlineSearchSuggestionViewModel port. */
private data class SearchTabSuggestionState(
    val history: List<SearchHistory> = emptyList(),
    val suggestions: List<String> = emptyList(),
    val items: List<YTItem> = emptyList(),
)

/**
 * History and local library suggestions come from [OnlineSearchSuggestionViewModel],
 * which reads the real SQLDelight database. Network suggestions stay empty until the
 * KMP innertube endpoint is ported (the view model keeps that behaviour behind a TODO).
 */
@Composable
private fun rememberSearchTabSuggestions(query: String): SearchTabSuggestionState {
    val container = LocalAppContainer.current
    val viewModel = appViewModel<OnlineSearchSuggestionViewModel>(
        factory = viewModelFactory {
            OnlineSearchSuggestionViewModel(
                requireNotNull(container) { "LocalAppContainer must be provided above SearchScreen" },
            )
        },
    )
    val state by viewModel.viewState.collectAsState()
    LaunchedEffect(query) {
        viewModel.query.value = query
    }
    return SearchTabSuggestionState(
        history = state.history,
        suggestions = state.suggestions,
        items = state.items,
    )
}

@Composable
fun SearchScreen(
    navController: NavController,
    pureBlack: Boolean,
) {
    val container = LocalAppContainer.current
    val coroutineScope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val focusRequester = remember { FocusRequester() }
    val playerConnection = LocalPlaybackController.current
    val pauseSearchHistory by rememberPreference(SettingsKeys.PauseSearchHistoryKey, defaultValue = false)
    val offlineMode by rememberPreference(SettingsKeys.OfflineModeKey, defaultValue = false)
    val profilePicStr by rememberPreference(SettingsKeys.ProfilePicUriKey, defaultValue = "")

    var query by remember { mutableStateOf(TextFieldValue()) }
    var active by remember { mutableStateOf(false) }

    val suggestionState = rememberSearchTabSuggestions(query.text)
    val moodViewModel = appViewModel<MoodAndGenresViewModel>(
        factory = viewModelFactory { MoodAndGenresViewModel() },
    )

    val browseListState = rememberLazyListState()
    val activeListState = rememberLazyListState()
    val glassState = remember { GlassState() }

    val collapse = rememberSearchTabCollapseProgress(browseListState)

    val activeProgress by animateFloatAsState(
        targetValue = if (active) 1f else 0f,
        animationSpec = tween(
            durationMillis = SearchTabCollapseDurationMs,
            easing = SearchTabCollapseEasing,
        ),
        label = "searchActive",
    )

    val progressProvider = remember(collapse, activeProgress) {
        { maxOf(collapse.value, activeProgress) }
    }

    val commit: (String) -> Unit = { raw ->
        val text = raw.trim()
        if (text.isNotEmpty()) {
            focusManager.clearFocus()
            active = false
            // TODO(search): YouTubeUrlParser is not ported, so video/artist
            // links currently go through the normal search results route.
            navController.navigate("search/${encodeSearchQuery(text)}")
            if (!pauseSearchHistory) {
                coroutineScope.launch(Dispatchers.Default) {
                    container?.database?.queries?.insertSearchHistory(text)
                }
            }
            if (offlineMode) {
                // Offline results are rendered by LocalSearchScreen / SearchActiveContent;
                // do not attempt a network search from the main tab.
                Unit
            }
        }
    }

    BackHandler(enabled = active) {
        active = false
        focusManager.clearFocus()
    }

    LaunchedEffect(active) {
        if (active) {
            runCatching { focusRequester.requestFocus() }
        } else {
            focusManager.clearFocus()
        }
    }

    // Two taps on the Search tab ask for the field. See requestSearchFocus().
    val focusAsked by SearchTabFocusSignal.requested.collectAsState()
    LaunchedEffect(focusAsked) {
        if (!focusAsked) return@LaunchedEffect
        SearchTabFocusSignal.requested.value = false
        if (active) {
            runCatching { focusRequester.requestFocus() }
        } else {
            active = true
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            focusManager.clearFocus()
            SearchTabFocusSignal.requested.value = false
        }
    }

    val showBrowse by remember { derivedStateOf { activeProgress < 0.995f } }
    val showActive by remember { derivedStateOf { activeProgress > 0.005f } }

    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .glassRoot(glassState, isActive = { progressProvider() > 0.01f }),
        ) {
            if (showBrowse) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = 1f - activeProgress },
                ) {
                    SearchBrowseContent(
                        listState = browseListState,
                        navController = navController,
                        viewModel = moodViewModel,
                    )
                }
            }
            if (showActive) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer { alpha = activeProgress },
                ) {
                    SearchActiveContent(
                        listState = activeListState,
                        query = query.text,
                        state = suggestionState,
                        onFill = { text ->
                            query = TextFieldValue(text, TextRange(text.length))
                        },
                        onSubmit = commit,
                        onDeleteHistory = { entry ->
                            deleteHistoryEntry(entry)
                        },
                        navController = navController,
                        playerConnection = playerConnection,
                    )
                }
            }
        }

        SearchTabTopBar(
            glassState = glassState,
            progressProvider = progressProvider,
            pureBlack = pureBlack,
            title = stringResource(Res.string.search),
            trailing = {
                SearchTabAvatar(
                    imageUrl = profilePicStr.ifBlank { null },
                    onClick = { navController.navigateToTab(Screens.Settings.route) },
                )
            },
        ) {
            SearchTabField(
                value = query,
                onValueChange = { query = it },
                onSearch = commit,
                placeholder = "Search for any tracks, albums, lyrics...",
                focused = active,
                leading = if (active) {
                    {
                        Icon(
                            painter = painterResource(Res.drawable.arrow_back),
                            contentDescription = null,
                            tint = GlassTokens.MutedContent,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    active = false
                                    focusManager.clearFocus()
                                },
                        )
                    }
                } else {
                    null
                },
                trailing = if (query.text.isNotEmpty()) {
                    {
                        Icon(
                            painter = painterResource(Res.drawable.close),
                            contentDescription = "Clear search",
                            tint = GlassTokens.MutedContent,
                            modifier = Modifier
                                .size(20.dp)
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { query = TextFieldValue("") },
                        )
                    }
                } else {
                    null
                },
                focusRequester = focusRequester,
                onFocusChanged = { focused -> if (focused) active = true },
                readOnlyClick = if (!active) {
                    { active = true }
                } else {
                    null
                },
            )
        }
    }
}

private fun encodeSearchQuery(value: String): String = buildString {
    for (char in value) {
        when (char) {
            ' ' -> append("%20")
            '%' -> append("%25")
            '#' -> append("%23")
            '?' -> append("%3F")
            '/' -> append("%2F")
            else -> append(char)
        }
    }
}

private fun deleteHistoryEntry(entry: SearchHistory) {
    // TODO(db): expose a delete-by-id helper on MusicDatabaseFacade. Upstream used
    // the Room DAO here; raw SQLDelight delete would need a new .sq statement.
}

@Composable
private fun rememberSearchTabCollapseProgress(listState: LazyListState): State<Float> =
    derivedStateOf {
        val scrolled = listState.firstVisibleItemIndex * 80f +
            listState.firstVisibleItemScrollOffset.coerceAtLeast(0)
        (scrolled / 80f).coerceIn(0f, 1f)
    }

@Composable
private fun SearchBrowseContent(
    listState: LazyListState,
    navController: NavController,
    viewModel: MoodAndGenresViewModel,
) {
    val sections by viewModel.moodAndGenres.collectAsState()
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = searchTabTopBarHeight(),
            bottom = 112.dp,
        ),
    ) {
        val list = sections
        if (list.isNullOrEmpty()) {
            item(key = "browse_empty") {
                GlassCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp),
                ) {
                    Text(
                        text = "Browse",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Mood and genre shelves are not available in the KMP innertube port yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = GlassTokens.MutedContent,
                    )
                }
            }
        } else {
            list.forEachIndexed { sectionIndex, section ->
                item(key = "header_${sectionIndex}_${section.title}") {
                    SearchTabSectionHeader(
                        title = section.title,
                        ruleBelow = true,
                    )
                }
                val rows = section.items.chunked(2)
                itemsIndexed(
                    items = rows,
                    key = { rowIndex, _ -> "row_${sectionIndex}_$rowIndex" },
                    contentType = { _, _ -> "moodRow" },
                ) { _, row ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 5.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        row.forEach { item ->
                            LaunchedEffect(item.endpoint.browseId, item.endpoint.params) {
                                viewModel.requestCovers(item.endpoint.browseId, item.endpoint.params)
                            }
                            SearchTabMoodTile(
                                title = item.title,
                                stripeColor = item.stripeColor,
                                covers = viewModel.covers[
                                    coverKey(item.endpoint.browseId, item.endpoint.params)
                                ].orEmpty(),
                                onClick = {
                                    navController.navigate(
                                        "genre/${item.endpoint.browseId}?params=${item.endpoint.params}",
                                    )
                                },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        if (row.size == 1) Spacer(modifier = Modifier.weight(1f))
                    }
                }
                item(key = "gap_${sectionIndex}") { Spacer(modifier = Modifier.height(22.dp)) }
            }
        }
    }
}

@Composable
private fun SearchActiveContent(
    listState: LazyListState,
    query: String,
    state: SearchTabSuggestionState,
    onFill: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onDeleteHistory: (SearchHistory) -> Unit,
    navController: NavController,
    playerConnection: com.example.musicfy.player.PlaybackController?,
) {
    val offlineMode by rememberPreference(SettingsKeys.OfflineModeKey, defaultValue = false)
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            top = searchTabTopBarHeight(withTitle = false),
            bottom = 112.dp,
        ),
    ) {
        if (query.isEmpty()) {
            if (state.history.isNotEmpty()) {
                item(key = "history_header") {
                    SearchTabSectionHeader(title = stringResource(Res.string.search_history))
                }
                items(
                    items = state.history,
                    key = { "history_${it.id}" },
                    contentType = { "history" },
                ) { entry ->
                    SearchTabSimpleRow(
                        text = entry.query,
                        leadingIcon = Res.drawable.history,
                        onClick = { onSubmit(entry.query) },
                        trailing = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                RowGlyph(Res.drawable.close) { onDeleteHistory(entry) }
                                Spacer(modifier = Modifier.width(4.dp))
                                // arrow_top_left is not in the generated KMP resource
                                // accessors; arrow_forward is the closest available glyph.
                                RowGlyph(Res.drawable.arrow_forward) { onFill(entry.query) }
                            }
                        },
                    )
                }
            }
        } else {
            if (state.suggestions.isNotEmpty()) {
                item(key = "suggestion_header") {
                    SearchTabSectionHeader(title = stringResource(Res.string.suggestions))
                }
                items(
                    items = state.suggestions,
                    key = { "suggestion_$it" },
                    contentType = { "suggestion" },
                ) { suggestion ->
                    SearchTabSimpleRow(
                        text = suggestion,
                        pill = true,
                        onClick = { onSubmit(suggestion) },
                        trailing = {
                            RowGlyph(Res.drawable.arrow_forward) { onFill(suggestion) }
                        },
                    )
                }
            }

            if (state.items.isNotEmpty()) {
                item(key = "top_result_header") {
                    Spacer(modifier = Modifier.height(10.dp))
                    SearchTabSectionHeader(title = stringResource(Res.string.top_result))
                }
                items(
                    items = state.items,
                    key = { "item_${it.id}" },
                    contentType = { "ytItem" },
                ) { item ->
                    SuggestionResultRow(
                        item = item,
                        onClick = {
                            when (item) {
                                is SongItem -> playerConnection?.let { player ->
                                    // TODO(search): YouTubeQueue.radio is not ported yet;
                                    // start with this single remote track.
                                    player.playQueue(
                                        ListQueue(
                                            title = item.title,
                                            items = listOf(item.toPlaybackTrack()),
                                        ),
                                    )
                                }

                                is AlbumItem -> navController.navigate("album/${item.id}")
                                is ArtistItem -> navController.navigate("artist/${item.id}")
                                is PlaylistItem -> navController.navigate("online_playlist/${item.id}")
                            }
                        },
                    )
                }
            }

            if (state.suggestions.isEmpty() && state.items.isEmpty()) {
                item(key = "suggest_loading") {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(180.dp)
                            .padding(horizontal = 20.dp)
                            .glassSurface(
                                shape = RoundedCornerShape(18.dp),
                                style = GlassStyle(elevation = 0.dp),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        if (offlineMode) {
                            Text(
                                text = "Offline mode: nothing in your library matches",
                                style = MaterialTheme.typography.bodySmall,
                                color = GlassTokens.MutedContent,
                            )
                        } else {
                            SearchTabLoadingDots()
                        }
                    }
                }
            }
        }
    }
}

private fun SongItem.toPlaybackTrack(): PlaybackTrack = PlaybackTrack(
    mediaId = id,
    metadata = MediaMetadata(
        id = id,
        title = title,
        artists = artists.map { MediaMetadata.Artist(id = it.id, name = it.name) },
        duration = duration ?: 0,
        thumbnailUrl = thumbnail,
        explicit = explicit,
    ),
    source = TrackSource.Remote(id),
)

@Composable
private fun RowGlyph(icon: DrawableResource, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(28.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            painter = painterResource(icon),
            contentDescription = null,
            tint = GlassTokens.MutedContent,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun SuggestionResultRow(
    item: YTItem,
    onClick: () -> Unit,
) {
    val subtitle = when (item) {
        is SongItem -> item.artists.joinToString { it.name }
        is AlbumItem -> "Album" + (item.artists?.takeIf { it.isNotEmpty() }
            ?.let { " • " + it.joinToString { a -> a.name } } ?: "")

        is ArtistItem -> "Artist"
        is PlaylistItem -> "Playlist" + (item.author?.name?.let { " • $it" } ?: "")
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clickable(onClick = onClick)
            .glassSurface(
                shape = RoundedCornerShape(16.dp),
                style = GlassStyle(elevation = 0.dp),
            )
            .padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SearchTabArtwork(
            url = item.thumbnail,
            size = 44.dp,
            circle = item is ArtistItem,
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.bodyLarge.copy(
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                ),
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                color = GlassTokens.MutedContent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

// -------------------------------------------------------------------------------------------------
// Local glass chrome. TODO(search-design): replace these private helpers with the shared ones.
// -------------------------------------------------------------------------------------------------

private val SearchTabHorizontalPadding = 20.dp
private val SearchTabCollapseEasing = CubicBezierEasing(0.57f, 0.53f, 0f, 1f)
private const val SearchTabCollapseDurationMs = 900

@Composable
private fun searchTabTopBarHeight(withTitle: Boolean = true): Dp =
    if (withTitle) 152.dp else 104.dp

@Composable
private fun SearchTabTopBar(
    glassState: GlassState,
    progressProvider: () -> Float,
    pureBlack: Boolean,
    title: String,
    trailing: @Composable () -> Unit,
    field: @Composable () -> Unit,
) {
    val fontFamily = interFontFamily()
    val collapse = progressProvider().coerceIn(0f, 1f)
    val base = if (pureBlack) Color(0xFF0A0A0C) else GlassTokens.Chrome
    val style = GlassStyle(
        containerColor = base.copy(alpha = if (pureBlack) 0.96f else 0.92f),
        borderColor = GlassTokens.Stroke,
        highlightColor = GlassTokens.Highlight,
        cornerRadius = 22.dp,
        elevation = 18.dp,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(
                shape = RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp),
                style = style,
            )
            .padding(horizontal = SearchTabHorizontalPadding)
            .padding(top = 12.dp, bottom = 14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(38.dp)
                .graphicsLayer { alpha = 1f - collapse },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                painter = painterResource(Res.drawable.search),
                contentDescription = null,
                tint = glassState.accent,
                modifier = Modifier.size(20.dp),
            )
            Spacer(Modifier.width(10.dp))
            Text(
                text = title,
                fontFamily = fontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 19.sp,
                color = Color.White,
                modifier = Modifier.weight(1f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            trailing()
        }
        Spacer(Modifier.height(10.dp))
        field()
    }
}

@Composable
private fun SearchTabField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    onSearch: (String) -> Unit,
    placeholder: String,
    focused: Boolean,
    leading: (@Composable () -> Unit)?,
    trailing: (@Composable () -> Unit)?,
    focusRequester: FocusRequester,
    onFocusChanged: (Boolean) -> Unit,
    readOnlyClick: (() -> Unit)?,
) {
    val accent = LocalGlassState.current.accent
    val fontFamily = interFontFamily()
    val style = GlassStyle(
        containerColor = if (focused) Color(0xFF222226) else Color(0x14FFFFFF),
        borderColor = if (focused) accent.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.14f),
        highlightColor = Color.White.copy(alpha = 0.08f),
        cornerRadius = 22.dp,
        elevation = 0.dp,
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .then(readOnlyClick?.let { click -> Modifier.clickable { click() } } ?: Modifier)
            .glassSurface(shape = RoundedCornerShape(22.dp), style = style)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) {
            Box(Modifier.size(20.dp)) { leading() }
            Spacer(Modifier.width(8.dp))
        }
        Icon(
            painter = painterResource(Res.drawable.trending_up),
            contentDescription = null,
            tint = accent.copy(alpha = 0.8f),
            modifier = Modifier.size(18.dp),
        )
        Spacer(Modifier.width(10.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = fontFamily,
                fontSize = 14.sp,
                color = Color.White,
            ),
            cursorBrush = SolidColor(accent),
            // TODO(search): wire IME onSearch once a common KeyboardActions port is available.
            modifier = Modifier
                .weight(1f)
                .fillMaxHeight()
                .focusRequester(focusRequester)
                .onFocusChanged { onFocusChanged(it.isFocused) },
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (value.text.isEmpty()) {
                        Text(
                            text = placeholder,
                            fontFamily = fontFamily,
                            fontSize = 14.sp,
                            color = GlassTokens.MutedContent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    innerTextField()
                }
            },
        )
        if (trailing != null) {
            Spacer(Modifier.width(8.dp))
            Box(Modifier.size(20.dp)) { trailing() }
        }
    }
}

@Composable
private fun SearchTabAvatar(
    imageUrl: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val style = GlassStyle(
        containerColor = Color.White.copy(alpha = 0.08f),
        borderColor = Color.White.copy(alpha = 0.18f),
        highlightColor = Color.White.copy(alpha = 0.06f),
        cornerRadius = 18.dp,
        elevation = 0.dp,
    )
    Box(
        modifier = modifier
            .size(36.dp)
            .clip(CircleShape)
            .glassSurface(shape = CircleShape, style = style)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (imageUrl.isNullOrBlank()) {
            Icon(
                painter = painterResource(Res.drawable.person),
                contentDescription = "Profile",
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        } else {
            AsyncImage(
                model = imageUrl,
                contentDescription = "Profile",
                contentScale = ContentScale.Crop,
                placeholder = painterResource(Res.drawable.person),
                error = painterResource(Res.drawable.person),
                modifier = Modifier.fillMaxSize().clip(CircleShape),
            )
        }
    }
}

@Composable
private fun SearchTabSectionHeader(
    title: String,
    ruleAbove: Boolean = false,
    ruleBelow: Boolean = false,
) {
    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = SearchTabHorizontalPadding)) {
        if (ruleAbove) {
            Spacer(Modifier.height(1.dp).background(GlassTokens.Stroke.copy(alpha = 0.16f)))
        }
        Text(
            text = title,
            fontFamily = interFontFamily(),
            fontWeight = FontWeight.SemiBold,
            fontSize = 14.sp,
            color = GlassTokens.MutedContent,
            modifier = Modifier.padding(vertical = 10.dp),
        )
        if (ruleBelow) {
            Spacer(Modifier.height(1.dp).background(GlassTokens.Stroke.copy(alpha = 0.16f)))
        }
    }
}

@Composable
private fun SearchTabSimpleRow(
    text: String,
    modifier: Modifier = Modifier,
    leadingIcon: DrawableResource? = null,
    pill: Boolean = false,
    trailing: @Composable () -> Unit = {},
    onClick: () -> Unit,
) {
    val shape = if (pill) CircleShape else RoundedCornerShape(16.dp)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = SearchTabHorizontalPadding, vertical = 4.dp)
            .clickable(onClick = onClick)
            .glassSurface(shape = shape, style = GlassStyle(elevation = 0.dp))
            .padding(horizontal = 12.dp, vertical = 12.dp),
    ) {
        if (leadingIcon != null) {
            Icon(
                painter = painterResource(leadingIcon),
                contentDescription = null,
                tint = GlassTokens.MutedContent,
                modifier = Modifier.size(18.dp),
            )
            Spacer(Modifier.width(12.dp))
        }
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        trailing()
    }
}

@Composable
private fun SearchTabArtwork(
    url: String?,
    size: Dp,
    circle: Boolean = false,
) {
    val shape = if (circle) CircleShape else RoundedCornerShape(8.dp)
    Box(
        modifier = Modifier
            .size(size)
            .clip(shape)
            .glassSurface(shape = shape, style = GlassStyle(elevation = 0.dp)),
    ) {
        AsyncImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            placeholder = painterResource(Res.drawable.queue_music),
            error = painterResource(Res.drawable.queue_music),
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun SearchTabMoodTile(
    title: String,
    stripeColor: Long,
    covers: List<String>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tint = Color(stripeColor.toInt()).let {
        if (it == Color.Black) GlassTokens.Purple else it
    }
    Column(
        modifier = modifier
            .height(120.dp)
            .clickable(onClick = onClick)
            .glassSurface(
                shape = RoundedCornerShape(20.dp),
                style = GlassStyle(elevation = 0.dp),
            )
            .padding(12.dp),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(tint, RoundedCornerShape(2.dp)),
        )
        Spacer(Modifier.height(10.dp))
        Row(horizontalArrangement = Arrangement.spacedBy((-12).dp)) {
            covers.take(3).forEach { url ->
                SearchTabArtwork(url = url, size = 40.dp, circle = false)
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = Color.White,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun SearchTabLoadingDots(modifier: Modifier = Modifier) {
    Text(
        text = "•••",
        style = MaterialTheme.typography.titleLarge,
        color = GlassTokens.MutedContent,
        modifier = modifier,
    )
}
