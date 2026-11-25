package com.example.musicfy.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.example.musicfy.constants.SettingsDefaults
import com.example.musicfy.resources.Res
import com.example.musicfy.resources.cached
import com.example.musicfy.resources.close
import com.example.musicfy.resources.info
import com.example.musicfy.resources.library_add_check
import com.example.musicfy.resources.music_note
import com.example.musicfy.resources.person
import com.example.musicfy.resources.play
import com.example.musicfy.resources.settings
import com.example.musicfy.resources.settings_outline
import com.example.musicfy.resources.settings_custom_font_default
import com.example.musicfy.resources.settings_custom_font_description
import com.example.musicfy.resources.settings_custom_font_monospace
import com.example.musicfy.resources.settings_custom_font_system
import com.example.musicfy.resources.settings_custom_font_title
import com.example.musicfy.resources.settings_search_clear
import com.example.musicfy.resources.settings_search_no_results
import com.example.musicfy.resources.settings_search_placeholder
import com.example.musicfy.settings.SettingsKeys
import com.example.musicfy.settings.rememberPreference
import com.example.musicfy.ui.component.GlassCard
import com.example.musicfy.ui.component.GlassStyle
import com.example.musicfy.ui.component.LocalGlassState
import com.example.musicfy.ui.component.SettingsHighlight
import com.example.musicfy.ui.component.glassSurface
import com.example.musicfy.ui.component.navigateToTab
import com.example.musicfy.ui.screens.Routes
import com.example.musicfy.ui.screens.Screens
import com.example.musicfy.ui.theme.interFontFamily
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

private val SettingsTopBarHeight = 78.dp

/**
 * KMP port of upstream `MusicfySettingsScreen`.
 *
 * The Android-only inputs are gone (Activity/Hilt, Android BackHandler,
 * `painterResource(Int)`, `LocalPlayerAwareWindowInsets`, the search-design
 * helpers and the move-to-Mochy sheet). The glass chrome is built locally from
 * [GlassCard], [glassSurface] and [LocalGlassState] so this screen can compile
 * without waiting on the shared search/settings component ports.
 *
 * TODO(search-design): replace [SettingsGlassTopBar] and [SettingsAvatar] with
 * the shared `SearchGlassTopBar` / `SearchField` / `SearchAvatar` once that
 * common port lands; the call site below is deliberately kept small.
 */
@Composable
fun MusicfySettingsScreen(navController: NavHostController) {
    val glassState = LocalGlassState.current
    val fontFamily = interFontFamily()
    val focusManager = LocalFocusManager.current
    val scrollState = rememberScrollState()

    val (profilePicUri) = rememberPreference(SettingsKeys.ProfilePicUriKey, "")

    var query by remember { mutableStateOf(TextFieldValue()) }
    var isSearchFocused by remember { mutableStateOf(false) }
    val isSearching = isSearchFocused || query.text.isNotBlank()

    val recommendedItem = remember { RecommendedOptions.random() }

    val results = remember(query.text) { searchSettings(query.text) }

    // Content fades while the search field is active, as on Android.
    val contentAlpha by animateFloatAsState(
        targetValue = if (isSearching) 0f else 1f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "settingsContentAlpha",
    )

    val cardStyle = GlassStyle(
        containerColor = glassState.accent.copy(alpha = 0.12f),
        borderColor = Color.White.copy(alpha = 0.14f),
        highlightColor = Color.White.copy(alpha = 0.10f),
        cornerRadius = 22.dp,
        elevation = 12.dp,
    )
    val iconStyle = GlassStyle(
        containerColor = Color.White.copy(alpha = 0.08f),
        borderColor = Color.White.copy(alpha = 0.12f),
        highlightColor = Color.White.copy(alpha = 0.06f),
        cornerRadius = 20.dp,
        elevation = 0.dp,
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
        ) {
            Spacer(modifier = Modifier.height(SettingsTopBarHeight))

            AnimatedVisibility(
                visible = query.text.isNotBlank(),
                enter = fadeIn(tween(160, easing = FastOutSlowInEasing)) +
                    slideInVertically(tween(160, easing = FastOutSlowInEasing)) { 16 },
                exit = fadeOut(tween(120, easing = FastOutSlowInEasing)),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (results.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.settings_search_no_results, query.text),
                            fontFamily = fontFamily,
                            fontSize = 15.sp,
                            color = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 12.dp),
                        )
                    } else {
                        results.forEach { entry ->
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .glassSurface(shape = RoundedCornerShape(18.dp), style = cardStyle)
                                    .clickable {
                                        SettingsHighlight.pending = entry.highlightKey
                                        navController.navigate(entry.route)
                                    }
                                    .padding(horizontal = 14.dp, vertical = 12.dp),
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .glassSurface(shape = CircleShape, style = iconStyle),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        Icon(
                                            painter = painterResource(Res.drawable.settings_outline),
                                            contentDescription = null,
                                            tint = glassState.accent,
                                            modifier = Modifier.size(18.dp),
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(11.dp))
                                    Column {
                                        Text(
                                            text = entry.title,
                                            fontFamily = fontFamily,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 16.sp,
                                            color = Color.White,
                                        )
                                        Text(
                                            text = entry.page,
                                            fontFamily = fontFamily,
                                            fontSize = 12.sp,
                                            color = Color.White.copy(alpha = 0.55f),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (contentAlpha > 0.001f) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer { alpha = contentAlpha },
                ) {
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp)
                            .clickable {
                                SettingsHighlight.pending = recommendedItem.highlightKey
                                recommendedItem.onClick(navController)
                            },
                        style = cardStyle,
                    ) {
                        Text(
                            text = "Reccomended item",
                            fontFamily = fontFamily,
                            fontWeight = FontWeight.Medium,
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.55f),
                        )

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .glassSurface(shape = CircleShape, style = iconStyle),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    painter = painterResource(recommendedItem.icon),
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp),
                                )
                            }

                            Spacer(modifier = Modifier.width(12.dp))

                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = recommendedItem.title,
                                    fontFamily = fontFamily,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 16.sp,
                                    color = Color.White,
                                )
                                Text(
                                    text = recommendedItem.breadcrumb,
                                    fontFamily = fontFamily,
                                    fontSize = 13.sp,
                                    color = Color.White.copy(alpha = 0.55f),
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp)
                            .height(0.8.dp)
                            .background(Color.White.copy(alpha = 0.10f)),
                    )

                    Spacer(modifier = Modifier.height(18.dp))

                    CustomFontPreferenceCard()

                    Spacer(modifier = Modifier.height(18.dp))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        CategoryBubble(
                            title = "Move to Mochy Music",
                            icon = Res.drawable.person,
                            onClick = {
                                // TODO: show the shared move-to-Mochy sheet once its KMP port exists.
                            },
                        )

                        CategoryBubble(
                            title = "General",
                            icon = Res.drawable.settings,
                            onClick = { navController.navigate(Routes.GENERAL_SETTINGS) },
                        )

                        CategoryBubble(
                            title = "Appearance",
                            icon = Res.drawable.settings_outline,
                            onClick = { navController.navigate(Routes.APPEARANCE_SETTINGS) },
                        )

                        CategoryBubble(
                            title = "Playback",
                            icon = Res.drawable.play,
                            onClick = { navController.navigate(Routes.PLAYBACK_SETTINGS) },
                        )

                        CategoryBubble(
                            title = "Gesture",
                            icon = Res.drawable.cached,
                            onClick = { navController.navigate(Routes.GESTURE_SETTINGS) },
                        )

                        CategoryBubble(
                            title = "Storage",
                            icon = Res.drawable.cached,
                            onClick = { navController.navigate(Routes.STORAGE) },
                        )

                        CategoryBubble(
                            title = "Audio Lab",
                            icon = Res.drawable.music_note,
                            onClick = { navController.navigate(Routes.AUDIO_LAB) },
                        )

                        CategoryBubble(
                            title = "Modules",
                            icon = Res.drawable.library_add_check,
                            onClick = { navController.navigate(Routes.MODULES) },
                        )

                        CategoryBubble(
                            title = "Integrations",
                            icon = Res.drawable.info,
                            onClick = { navController.navigate(SettingsRoutes.Integrations) },
                        )
                    }
                }
            }

            // Headroom for the glass nav pill and mini player; upstream used
            // LocalPlayerAwareWindowInsets, which has no common equivalent yet.
            Spacer(modifier = Modifier.height(180.dp))
        }

        SettingsGlassTopBar(
            query = query,
            onQueryChange = { query = it },
            onSearchFocusChanged = { isSearchFocused = it },
            onClear = {
                query = TextFieldValue()
                isSearchFocused = false
                focusManager.clearFocus()
            },
            avatarUri = profilePicUri,
            onAvatarClick = {
                // Back to the profile page these were opened from, not a second copy on top.
                if (!navController.popBackStack(Screens.Settings.route, inclusive = false)) {
                    navController.navigateToTab(Screens.Settings.route)
                }
            },
            modifier = Modifier.align(Alignment.TopCenter),
        )
    }
}

/** One independently frosted category pill. */
@Composable
internal fun CategoryBubble(
    title: String,
    icon: DrawableResource,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glassState = LocalGlassState.current
    val style = GlassStyle(
        containerColor = Color.White.copy(alpha = 0.06f),
        borderColor = Color.White.copy(alpha = 0.12f),
        highlightColor = glassState.accent.copy(alpha = 0.10f),
        cornerRadius = 20.dp,
        elevation = 8.dp,
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(64.dp)
            .glassSurface(shape = RoundedCornerShape(20.dp), style = style)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        contentAlignment = Alignment.CenterStart,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .glassSurface(
                        shape = CircleShape,
                        style = style.copy(
                            containerColor = Color.White.copy(alpha = 0.08f),
                            elevation = 0.dp,
                        ),
                    ),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    painter = painterResource(icon),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(20.dp),
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text(
                text = title,
                fontFamily = interFontFamily(),
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                color = Color.White,
            )
        }
    }
}

/**
 * Small app-wide font selector. The enum name is written to
 * [SettingsKeys.CustomFontModeKey], so the only stored values are
 * DEFAULT / SYSTEM / MONOSPACE and the theme layer can read the same key.
 */
@Composable
internal fun CustomFontPreferenceCard(modifier: Modifier = Modifier) {
    val glassState = LocalGlassState.current
    val fontFamily = interFontFamily()
    val (storedMode, setStoredMode) = rememberPreference(
        key = SettingsKeys.CustomFontModeKey,
        defaultValue = SettingsDefaults.CustomFontMode,
    )
    val currentMode = SettingsFontMode.fromStoredValue(storedMode)
    val options = listOf(
        SettingsFontMode.DEFAULT to stringResource(Res.string.settings_custom_font_default),
        SettingsFontMode.SYSTEM to stringResource(Res.string.settings_custom_font_system),
        SettingsFontMode.MONOSPACE to stringResource(Res.string.settings_custom_font_monospace),
    )

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        style = GlassStyle(
            containerColor = glassState.accent.copy(alpha = 0.12f),
            borderColor = Color.White.copy(alpha = 0.14f),
            highlightColor = Color.White.copy(alpha = 0.10f),
            cornerRadius = 22.dp,
            elevation = 12.dp,
        ),
    ) {
        Text(
            text = stringResource(Res.string.settings_custom_font_title),
            fontFamily = fontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp,
            color = Color.White,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = stringResource(Res.string.settings_custom_font_description),
            fontFamily = fontFamily,
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.55f),
        )
        Spacer(modifier = Modifier.height(14.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { (mode, label) ->
                val selected = mode == currentMode
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .glassSurface(
                            shape = RoundedCornerShape(16.dp),
                            style = GlassStyle(
                                containerColor = if (selected) {
                                    glassState.accent.copy(alpha = 0.24f)
                                } else {
                                    Color.White.copy(alpha = 0.06f)
                                },
                                borderColor = if (selected) {
                                    glassState.accent
                                } else {
                                    Color.White.copy(alpha = 0.12f)
                                },
                                highlightColor = Color.White.copy(alpha = 0.06f),
                                cornerRadius = 16.dp,
                                elevation = 0.dp,
                            ),
                        )
                        .clickable { setStoredMode(mode.name) }
                        .padding(vertical = 10.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = label,
                        fontFamily = fontFamily,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                        fontSize = 13.sp,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

/**
 * Self-contained glass top bar. TODO(search-design): swap this whole
 * composable for the shared `SearchGlassTopBar` when it is available.
 */
@Composable
private fun SettingsGlassTopBar(
    query: TextFieldValue,
    onQueryChange: (TextFieldValue) -> Unit,
    onSearchFocusChanged: (Boolean) -> Unit,
    onClear: () -> Unit,
    avatarUri: String,
    onAvatarClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val glassState = LocalGlassState.current
    val fontFamily = interFontFamily()
    val barStyle = GlassStyle(
        containerColor = glassState.accent.copy(alpha = 0.10f),
        borderColor = Color.White.copy(alpha = 0.12f),
        highlightColor = Color.White.copy(alpha = 0.10f),
        cornerRadius = 22.dp,
        elevation = 16.dp,
    )
    val fieldStyle = GlassStyle(
        containerColor = Color.White.copy(alpha = 0.06f),
        borderColor = Color.White.copy(alpha = 0.14f),
        highlightColor = Color.White.copy(alpha = 0.06f),
        cornerRadius = 21.dp,
        elevation = 0.dp,
    )

    Row(
        modifier = modifier
            .fillMaxWidth()
            .glassSurface(
                shape = RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp),
                style = barStyle,
            )
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(Res.drawable.settings_outline),
            contentDescription = null,
            tint = glassState.accent,
            modifier = Modifier.size(22.dp),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = "Settings",
            fontFamily = fontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 19.sp,
            color = Color.White,
        )
        Spacer(modifier = Modifier.width(12.dp))

        BasicTextField(
            value = query,
            onValueChange = onQueryChange,
            singleLine = true,
            textStyle = TextStyle(
                fontFamily = fontFamily,
                fontSize = 14.sp,
                color = Color.White,
            ),
            cursorBrush = SolidColor(glassState.accent),
            modifier = Modifier
                .weight(1f)
                .height(42.dp)
                .onFocusChanged { onSearchFocusChanged(it.isFocused) }
                .glassSurface(shape = CircleShape, style = fieldStyle)
                .padding(horizontal = 14.dp, vertical = 11.dp),
            decorationBox = { innerTextField ->
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    if (query.text.isEmpty()) {
                        Text(
                            text = stringResource(Res.string.settings_search_placeholder),
                            fontFamily = fontFamily,
                            fontSize = 14.sp,
                            color = Color.White.copy(alpha = 0.5f),
                        )
                    }
                    innerTextField()
                }
            },
        )

        if (query.text.isNotEmpty()) {
            Spacer(modifier = Modifier.width(6.dp))
            Icon(
                painter = painterResource(Res.drawable.close),
                contentDescription = stringResource(Res.string.settings_search_clear),
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier
                    .size(18.dp)
                    .clickable(onClick = onClear),
            )
        }

        Spacer(modifier = Modifier.width(10.dp))
        SettingsAvatar(imageUri = avatarUri, onClick = onAvatarClick)
    }
}

/** Local avatar; TODO(search-design): use the shared `SearchAvatar`. */
@Composable
private fun SettingsAvatar(
    imageUri: String,
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
        if (imageUri.isBlank()) {
            Icon(
                painter = painterResource(Res.drawable.person),
                contentDescription = "Profile",
                tint = Color.White,
                modifier = Modifier.size(20.dp),
            )
        } else {
            AsyncImage(
                model = imageUri,
                contentDescription = "Profile",
                contentScale = ContentScale.Crop,
                placeholder = painterResource(Res.drawable.person),
                error = painterResource(Res.drawable.person),
                modifier = Modifier.fillMaxSize().clip(CircleShape),
            )
        }
    }
}
