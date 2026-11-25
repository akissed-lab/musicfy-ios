package com.example.musicfy.ui.player

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import coil3.compose.AsyncImage
import com.example.musicfy.LocalPlayerUiState
import com.example.musicfy.db.entities.LyricsEntity
import com.example.musicfy.lyrics.LyricsUtils
import com.example.musicfy.player.LocalPlaybackController
import com.example.musicfy.player.PlaybackController
import com.example.musicfy.player.PlayerUiState
import com.example.musicfy.player.model.TrackInfo
import com.example.musicfy.resources.Res
import com.example.musicfy.resources.error_playback_failed
import com.example.musicfy.resources.lyrics
import com.example.musicfy.resources.music_note
import com.example.musicfy.resources.now_playing
import com.example.musicfy.resources.queue
import com.example.musicfy.resources.retry
import com.example.musicfy.ui.component.BackHandler
import com.example.musicfy.ui.component.GlassCard
import com.example.musicfy.ui.component.GlassStyle
import com.example.musicfy.ui.component.GlassTokens
import com.example.musicfy.ui.component.LocalGlassState
import com.example.musicfy.ui.component.glassSurface
import com.example.musicfy.ui.player.models.CardMorphState
import com.example.musicfy.ui.player.models.LikeBurstState
import com.example.musicfy.ui.player.models.PlayerAction
import com.example.musicfy.ui.player.models.PlayerPage
import com.example.musicfy.ui.player.models.PlayerSheetState
import com.example.musicfy.ui.player.models.rememberCardMorphState
import com.example.musicfy.ui.player.models.rememberLikeBurstState
import com.example.musicfy.ui.player.songinfo.SongInfoDetails
import com.example.musicfy.ui.player.songinfo.SongInfoSheet
import com.example.musicfy.ui.player.songinfo.from
import com.example.musicfy.ui.player.styles.PlayerStyleKit
import com.example.musicfy.ui.utils.stableSystemBars
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * The expanded player sheet.
 *
 * The Android original was a 1700-line coordinator of per-frame card morphs and shared-element
 * flights. This port keeps the same entry point, page structure and control composition, using the
 * shared glass primitives, the ported [PlaybackController] flows and one song carousel for every
 * moving part.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun BottomSheetPlayer(
    state: PlayerSheetState,
    navController: NavController,
    modifier: Modifier = Modifier,
    pureBlack: Boolean = false,
) {
    val controller = LocalPlaybackController.current ?: return
    val uiState = LocalPlayerUiState.current ?: return
    if (!state.expanded && state.progress <= 0.01f) return

    val carousel = rememberSongCarousel(controller)
    CompositionLocalProvider(LocalSongCarousel provides carousel) {
        PlayerSheet(
            sheetState = state,
            navController = navController,
            modifier = modifier,
            pureBlack = pureBlack,
            controller = controller,
            uiState = uiState,
            carousel = carousel,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerSheet(
    sheetState: PlayerSheetState,
    navController: NavController,
    modifier: Modifier,
    pureBlack: Boolean,
    controller: PlaybackController,
    uiState: PlayerUiState,
    carousel: SongCarousel,
) {
    val track by uiState.trackInfo.collectAsState()
    val player by controller.playerState.collectAsState()
    val currentLyrics by controller.currentLyrics.collectAsState()

    val handleState = rememberPlayerHandleState()
    val morph = rememberCardMorphState()
    val likeBurst = rememberLikeBurstState()
    var showSongInfo by remember { mutableStateOf(false) }
    val insets = WindowInsets.stableSystemBars.asPaddingValues()
    val scrim = if (pureBlack) Color.Black else GlassTokens.Base

    val failureProgress = rememberStreamFailureProgress()
    val failureLabel by rememberStreamFailureLabel(failureProgress)
    val failureText = failureLabel

    val accent = when ((track.title.hashCode() and 0x7fffffff) % 3) {
        0 -> GlassTokens.Pink
        1 -> GlassTokens.Purple
        else -> GlassTokens.Blue
    }
    val glass = LocalGlassState.current
    LaunchedEffect(accent) { glass.accent = accent }

    val sheetAlpha by animateFloatAsState(
        targetValue = sheetState.progress.coerceIn(0f, 1f),
        label = "playerSheetAlpha",
    )

    BackHandler(enabled = sheetState.expanded) {
        if (sheetState.page != PlayerPage.PLAYER) {
            sheetState.closePage()
        } else {
            sheetState.collapse()
            navController.navigateUp()
        }
    }

    Box(modifier = modifier.fillMaxSize().background(scrim)) {
        CarouselCrossfade(
            carousel = carousel,
            fallbackUrl = track.thumbnailUrl,
            modifier = Modifier.fillMaxSize(),
        ) { url ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxSize()
                    .blur(48.dp)
                    .alpha(0.34f),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(listOf(Color.Transparent, scrim.copy(alpha = 0.94f))),
                ),
        )

        GlassCard(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .fillMaxHeight(0.86f)
                .padding(horizontal = 10.dp)
                .padding(bottom = insets.calculateBottomPadding())
                .streamFailureOverlay(failureProgress)
                .graphicsLayer {
                    alpha = sheetAlpha
                    translationY = (1f - sheetState.progress) * 72f
                },
            style = GlassStyle(cornerRadius = PlayerStyleKit.CardCorner, elevation = 24.dp),
        ) {
            PlayerHandle(
                state = handleState,
                sheet = sheetState,
                statusBarTop = insets.calculateTopPadding(),
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
            Spacer(Modifier.height(6.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = PlayerStyleKit.ContentPadding),
            ) {
                val tabs = listOf(
                    PlayerPage.PLAYER to stringResource(Res.string.now_playing),
                    PlayerPage.LYRICS to stringResource(Res.string.lyrics),
                    PlayerPage.QUEUE to stringResource(Res.string.queue),
                )
                tabs.forEach { (page, label) ->
                    val selected = sheetState.page == page
                    TextButton(
                        onClick = { sheetState.openPage(page) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text(
                            text = label,
                            style = PlayerStyleKit.textStyle(12),
                            color = if (selected) accent else GlassTokens.MutedContent,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }

            if (failureText != null) {
                Text(
                    text = failureText,
                    color = StreamFailureRed,
                    style = PlayerStyleKit.textStyle(11),
                    maxLines = 1,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = PlayerStyleKit.ContentPadding),
                )
            }

            when (sheetState.page) {
                PlayerPage.PLAYER -> PlayerPageContent(
                    controller = controller,
                    carousel = carousel,
                    track = track,
                    errorMessage = player.error?.message,
                    onOpenPage = { page -> sheetState.openPage(page) },
                    sheetProgress = { sheetState.progress },
                    onSongInfoClick = { showSongInfo = true },
                    modifier = Modifier.weight(1f),
                )

                PlayerPage.LYRICS -> LyricsPageContent(
                    currentLyrics = currentLyrics,
                    morph = morph,
                    modifier = Modifier.weight(1f),
                )

                PlayerPage.QUEUE -> QueueScreen(
                    onClose = { sheetState.openPage(PlayerPage.PLAYER) },
                    contentBottomInset = 24.dp,
                    controlsClearance = 88.dp,
                    likeBurst = likeBurst,
                    enterProgressProvider = { sheetState.progress },
                    morph = morph,
                    modifier = Modifier.weight(1f),
                )
            }
        }

        if (showSongInfo) {
            SongInfoSheet(
                details = SongInfoDetails.from(track),
                onDismiss = { showSongInfo = false },
            )
        }
    }
}

@Composable
private fun PlayerPageContent(
    controller: PlaybackController,
    carousel: SongCarousel,
    track: TrackInfo,
    errorMessage: String?,
    onOpenPage: (PlayerPage) -> Unit,
    sheetProgress: () -> Float,
    onSongInfoClick: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.fillMaxWidth(),
    ) {
        CarouselPictures(
            carousel = carousel,
            fallbackUrl = track.thumbnailUrl,
            modifier = Modifier
                .padding(top = 8.dp)
                .size(220.dp),
        ) { url, pictureModifier ->
            AsyncImage(
                model = url,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                placeholder = painterResource(Res.drawable.music_note),
                error = painterResource(Res.drawable.music_note),
                modifier = pictureModifier
                    .clip(RoundedCornerShape(24.dp))
                    .glassSurface(
                        shape = RoundedCornerShape(24.dp),
                        style = GlassStyle(elevation = 8.dp),
                    ),
            )
        }

        Spacer(Modifier.height(14.dp))
        PlayerControls(onSongInfoClick = onSongInfoClick)
        Spacer(Modifier.height(12.dp))

        PlayerVolumeBar(
            isActive = { action ->
                when (action) {
                    PlayerAction.LYRICS,
                    PlayerAction.QUEUE,
                    -> false
                    else -> false
                }
            },
            onAction = { action ->
                when (action) {
                    PlayerAction.LYRICS -> onOpenPage(PlayerPage.LYRICS)
                    PlayerAction.QUEUE -> onOpenPage(PlayerPage.QUEUE)
                    PlayerAction.STUDIO -> controller.requestPlayerStudio()
                    else -> Unit
                }
            },
            progressProvider = sheetProgress,
        )

        if (errorMessage != null) {
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(Res.string.error_playback_failed),
                style = PlayerStyleKit.textStyle(12),
                color = StreamFailureRed,
                maxLines = 1,
            )
            Text(
                text = errorMessage,
                style = PlayerStyleKit.textStyle(11),
                color = GlassTokens.MutedContent,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            TextButton(onClick = { controller.play() }) {
                Text(stringResource(Res.string.retry))
            }
        }
    }
}

@Composable
private fun LyricsPageContent(
    currentLyrics: LyricsEntity?,
    morph: CardMorphState,
    modifier: Modifier = Modifier,
) {
    val lines = rememberLyricsLines(currentLyrics)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = PlayerStyleKit.ContentPadding),
    ) {
        Text(
            text = stringResource(Res.string.lyrics),
            style = PlayerStyleKit.textStyle(22),
            color = Color.White,
        )
        Spacer(Modifier.height(12.dp))
        MiniLyricsTicker(
            lines = lines,
            loaded = currentLyrics != null,
            morph = morph,
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp),
        )
    }
}

@Composable
private fun rememberLyricsLines(currentLyrics: LyricsEntity?): List<com.example.musicfy.lyrics.LyricsEntry> =
    androidx.compose.runtime.remember(currentLyrics) {
        val raw = currentLyrics?.lyrics
        if (raw.isNullOrBlank() || raw == LyricsEntity.LYRICS_NOT_FOUND) {
            emptyList()
        } else {
            runCatching { LyricsUtils.parseLyrics(raw) }.getOrDefault(emptyList())
        }
    }
