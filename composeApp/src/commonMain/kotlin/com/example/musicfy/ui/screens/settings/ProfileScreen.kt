package com.example.musicfy.ui.screens.settings

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import coil3.compose.AsyncImage
import com.example.musicfy.LocalAppContainer
import com.example.musicfy.resources.Res
import com.example.musicfy.resources.expand_more
import com.example.musicfy.resources.music_note
import com.example.musicfy.resources.person
import com.example.musicfy.resources.settings
import com.example.musicfy.settings.SettingsKeys
import com.example.musicfy.settings.rememberPreference
import com.example.musicfy.ui.component.GlassCard
import com.example.musicfy.ui.component.GlassStyle
import com.example.musicfy.ui.component.LocalGlassState
import com.example.musicfy.ui.component.glassSurface
import com.example.musicfy.ui.screens.Routes
import com.example.musicfy.ui.theme.interFontFamily
import com.example.musicfy.util.nowLocal
import com.example.musicfy.viewmodels.ListenTotal
import com.example.musicfy.viewmodels.UserProfileViewModel
import com.example.musicfy.viewmodels.appViewModel
import com.example.musicfy.viewmodels.containerViewModelFactory
import kotlin.random.Random
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.painterResource

/**
 * KMP port of upstream `ProfileScreen`, the real destination for the fourth
 * main tab (`Screens.Settings.route`).
 *
 * Android-only pieces are intentionally omitted/stubbed: Hilt (replaced by
 * [LocalAppContainer] + [appViewModel]), `ProcessLifecycleOwner` greeting
 * rotation, inner-tube account lookup, BackHandler, device tilt/scratch-off
 * recap, `ProfileMorphHost` and the update/about sheets. What remains is the
 * profile ID card, listening time, version, developer and settings rows, all
 * on the shared liquid-glass system.
 */
@Composable
fun ProfileScreen(navController: NavHostController) {
    val glassState = LocalGlassState.current
    val fontFamily = interFontFamily()
    val scrollState = rememberScrollState()
    val collapseProgress by remember {
        derivedStateOf { (scrollState.value / 240f).coerceIn(0f, 1f) }
    }

    // Upstream rotates this on app foregrounding; ProcessLifecycleOwner has no
    // common equivalent, so it is picked once per composition for now.
    val greeting = remember {
        listOf("Hello again", "Hey", "Welcome back", "Good to see you").random()
    }

    val (localUsername) = rememberPreference(SettingsKeys.UsernameKey, "")
    val (profilePicUri) = rememberPreference(SettingsKeys.ProfilePicUriKey, "")
    val (cardNumberPref, setCardNumber) = rememberPreference(SettingsKeys.ProfileCardNumberKey, "")
    val (joinedEpochDay, setJoinedEpochDay) = rememberPreference(SettingsKeys.ProfileJoinedEpochDayKey, -1L)
    // TODO(account): resolve the live account name from InnerTubeCookieKey once
    // the network/account layer is ported. Discord presence is stubbed too.

    val container = LocalAppContainer.current
    val profileViewModel = container?.let { appContainer ->
        appViewModel<UserProfileViewModel>(
            factory = containerViewModelFactory<UserProfileViewModel>(appContainer) { UserProfileViewModel(it) },
        )
    }
    val listensState = profileViewModel?.listens?.collectAsState(initial = ListenTotal.Placeholder)
        ?: remember { mutableStateOf(ListenTotal.Placeholder) }
    val listens by listensState

    // The onboarding writes these; installs from before it get them once here.
    LaunchedEffect(cardNumberPref, joinedEpochDay) {
        if (cardNumberPref.isBlank()) {
            setCardNumber(Random.nextLong(10_000_000_000L, 100_000_000_000L).toString())
        }
        if (joinedEpochDay < 0L) {
            setJoinedEpochDay(nowLocal().date.toEpochDays().toLong())
        }
    }

    val accountName = localUsername
    val cardNumber = cardNumberPref.ifBlank { "00000000000" }
    val joinedDate = if (joinedEpochDay >= 0L) {
        LocalDate.fromEpochDays(joinedEpochDay.toInt())
    } else {
        nowLocal().date
    }
    val joinedText = "joined at $joinedDate"
    val allTimeMs = listens?.firstOrNull { it.label == "ALL TIME" }?.playTimeMs ?: 0L

    val cardStyle = GlassStyle(
        containerColor = glassState.accent.copy(alpha = 0.12f),
        borderColor = Color.White.copy(alpha = 0.16f),
        highlightColor = Color.White.copy(alpha = 0.10f),
        cornerRadius = 24.dp,
        elevation = 14.dp,
    )
    val subtleStyle = GlassStyle(
        containerColor = Color.White.copy(alpha = 0.05f),
        borderColor = Color.White.copy(alpha = 0.12f),
        highlightColor = Color.White.copy(alpha = 0.06f),
        cornerRadius = 22.dp,
        elevation = 8.dp,
    )

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState),
        ) {
            // ---- header: logo, greeting, ID card and pull hint -------------------------------
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(352.dp)
                    .background(
                        brush = Brush.verticalGradient(
                            listOf(
                                glassState.accent.copy(alpha = 0.18f),
                                Color.Transparent,
                            ),
                        ),
                        shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
                    )
                    .glassSurface(
                        shape = RoundedCornerShape(bottomStart = 28.dp, bottomEnd = 28.dp),
                        style = subtleStyle.copy(cornerRadius = 28.dp),
                    ),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 20.dp, vertical = 26.dp),
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            painter = painterResource(Res.drawable.music_note),
                            contentDescription = "Musicfy",
                            tint = Color.White,
                            modifier = Modifier.size(28.dp),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "MUSICFY",
                            fontFamily = fontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 15.sp,
                            color = Color.White.copy(alpha = 0.75f),
                        )
                    }

                    Spacer(modifier = Modifier.height(22.dp))

                    Text(
                        text = "$greeting, ${accountName.ifBlank { "there" }}!",
                        fontFamily = fontFamily,
                        fontWeight = FontWeight.Bold,
                        fontSize = 23.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = Color.White,
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    ProfileIdCard(
                        accountName = accountName,
                        photoUri = profilePicUri,
                        cardNumber = cardNumber,
                        joinedText = joinedText,
                        fontFamily = fontFamily,
                        accent = glassState.accent,
                        style = cardStyle,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                // TODO(profile): open ProfileMorphHost once its KMP port exists.
                            },
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Icon(
                        painter = painterResource(Res.drawable.expand_more),
                        contentDescription = "Pull down",
                        tint = Color.White.copy(alpha = 0.35f),
                        modifier = Modifier
                            .size(28.dp)
                            .align(Alignment.CenterHorizontally),
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            ListeningTimeCard(
                totalMs = allTimeMs,
                fontFamily = fontFamily,
                accent = glassState.accent,
                style = subtleStyle,
                onViewStats = { navController.navigate(Routes.STATS) },
            )

            Spacer(modifier = Modifier.height(16.dp))

            VersionCard(
                fontFamily = fontFamily,
                accent = glassState.accent,
                style = subtleStyle,
                onAboutClick = {
                    // TODO(update): show the shared about/update sheet once ported.
                },
            )

            Spacer(modifier = Modifier.height(16.dp))

            DeveloperCard(
                fontFamily = fontFamily,
                style = subtleStyle,
                onClick = {
                    navController.navigate(Routes.DEVELOPER_SPACE.replace("{person}", "0"))
                },
            )

            Spacer(modifier = Modifier.height(28.dp))

            Text(
                text = "Settings",
                fontFamily = fontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Color.White,
                modifier = Modifier.padding(start = 4.dp, bottom = 10.dp),
            )

            OpenSettingsRow(
                fontFamily = fontFamily,
                style = subtleStyle,
                onClick = { navController.navigate(Routes.MUSICFY_SETTINGS) },
            )

            Spacer(modifier = Modifier.height(180.dp))
        }

        // Collapsed glass chrome: sibling of the scroll box, never inside it.
        if (collapseProgress > 0.01f) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .glassSurface(
                        shape = RoundedCornerShape(bottomStart = 22.dp, bottomEnd = 22.dp),
                        style = subtleStyle.copy(cornerRadius = 22.dp),
                    )
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.music_note),
                    contentDescription = "Musicfy",
                    tint = Color.White,
                    modifier = Modifier.size(24.dp),
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "$greeting, ${accountName.ifBlank { "there" }}!",
                    fontFamily = fontFamily,
                    fontSize = 18.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun ProfileIdCard(
    accountName: String,
    photoUri: String,
    cardNumber: String,
    joinedText: String,
    fontFamily: FontFamily,
    accent: Color,
    style: GlassStyle,
    modifier: Modifier = Modifier,
) {
    GlassCard(modifier = modifier, style = style) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(52.dp)
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
                if (photoUri.isBlank()) {
                    Icon(
                        painter = painterResource(Res.drawable.person),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(26.dp),
                    )
                } else {
                    AsyncImage(
                        model = photoUri,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        placeholder = painterResource(Res.drawable.person),
                        error = painterResource(Res.drawable.person),
                        modifier = Modifier.fillMaxSize().clip(CircleShape),
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column {
                Text(
                    text = accountName.ifBlank { "there" },
                    fontFamily = fontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp,
                    color = Color.White,
                )
                Text(
                    text = joinedText,
                    fontFamily = fontFamily,
                    fontSize = 12.sp,
                    color = Color.White.copy(alpha = 0.55f),
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Color.White.copy(alpha = 0.08f)),
        )

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = cardNumber,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.Bold,
            fontSize = 19.sp,
            color = Color.White,
        )
        Text(
            text = "MUSICFY ID",
            fontFamily = fontFamily,
            fontSize = 11.sp,
            color = accent,
        )
    }
}

@Composable
private fun ListeningTimeCard(
    totalMs: Long,
    fontFamily: FontFamily,
    accent: Color,
    style: GlassStyle,
    onViewStats: () -> Unit,
) {
    val minutes = (totalMs / 60_000L).coerceAtLeast(0L)
    val hours = minutes / 60

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onViewStats),
        style = style,
    ) {
        Text(
            text = "Total Listening time",
            fontFamily = fontFamily,
            fontSize = 13.sp,
            color = Color.White.copy(alpha = 0.55f),
        )

        Spacer(modifier = Modifier.height(8.dp))

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = hours.toString(),
                fontFamily = fontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 40.sp,
                color = Color.White,
            )
            Text(
                text = "h",
                fontFamily = fontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color.White,
                modifier = Modifier.padding(start = 2.dp, bottom = 6.dp),
            )
            Spacer(modifier = Modifier.width(14.dp))
            Text(
                text = (minutes % 60).toString(),
                fontFamily = fontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 40.sp,
                color = Color.White,
            )
            Text(
                text = "m",
                fontFamily = fontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = Color.White,
                modifier = Modifier.padding(start = 2.dp, bottom = 6.dp),
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = "View Stats  >",
            fontFamily = fontFamily,
            fontWeight = FontWeight.SemiBold,
            fontSize = 13.sp,
            color = accent,
        )
    }
}

@Composable
private fun VersionCard(
    fontFamily: FontFamily,
    accent: Color,
    style: GlassStyle,
    onAboutClick: () -> Unit,
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                // TODO(update): open the shared update sheet when available.
            },
        style = style,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Version & about app",
                    fontFamily = fontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = Color.White.copy(alpha = 0.55f),
                )
                Text(
                    text = "1.0",
                    fontFamily = fontFamily,
                    fontWeight = FontWeight.Bold,
                    fontSize = 34.sp,
                    color = Color.White,
                )
                Text(
                    text = "Up to date",
                    fontFamily = fontFamily,
                    fontSize = 12.sp,
                    color = accent,
                )
            }

            Spacer(modifier = Modifier.width(10.dp))

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .glassSurface(
                        shape = CircleShape,
                        style = style.copy(
                            containerColor = Color.White.copy(alpha = 0.08f),
                            elevation = 0.dp,
                        ),
                    )
                    .clickable(onClick = onAboutClick)
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    text = "About app",
                    fontFamily = fontFamily,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    color = Color.White,
                )
            }
        }
    }
}

@Composable
private fun DeveloperCard(
    fontFamily: FontFamily,
    style: GlassStyle,
    onClick: () -> Unit,
) {
    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        style = style,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                horizontalArrangement = Arrangement.spacedBy((-8).dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                repeat(3) { index ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .glassSurface(
                                shape = CircleShape,
                                style = style.copy(
                                    containerColor = Color.White.copy(alpha = 0.10f),
                                    elevation = 0.dp,
                                ),
                            ),
                    ) {
                        Text(
                            text = ('A'.code + index).toChar().toString(),
                            fontFamily = fontFamily,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp,
                            color = Color.White,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(18.dp))

            Text(
                text = "meet da dev and contributors :3",
                fontFamily = fontFamily,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Start,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun OpenSettingsRow(
    fontFamily: FontFamily,
    style: GlassStyle,
    onClick: () -> Unit,
) {
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .glassSurface(shape = RoundedCornerShape(22.dp), style = style)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .glassSurface(
                        shape = CircleShape,
                        style = style.copy(
                            containerColor = Color.White.copy(alpha = 0.10f),
                            elevation = 0.dp,
                        ),
                    ),
            ) {
                Icon(
                    painter = painterResource(Res.drawable.settings),
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(18.dp),
                )
            }

            Spacer(modifier = Modifier.width(14.dp))

            Text(
                text = "Open settings",
                fontFamily = fontFamily,
                fontWeight = FontWeight.SemiBold,
                fontSize = 17.sp,
                color = Color.White,
            )
        }
    }
}
