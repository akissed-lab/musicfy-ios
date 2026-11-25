package com.example.musicfy.ui.component

import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.musicfy.LocalPlayerUiState
import com.example.musicfy.player.LocalPlaybackController
import com.example.musicfy.resources.Res
import com.example.musicfy.resources.expand_more
import com.example.musicfy.resources.now_playing
import com.example.musicfy.resources.pause
import com.example.musicfy.resources.play
import com.example.musicfy.settings.SettingsKeys
import com.example.musicfy.settings.rememberPreference
import com.example.musicfy.ui.player.BottomSheetPlayer
import com.example.musicfy.ui.player.models.rememberPlayerSheetState
import com.example.musicfy.ui.screens.Routes
import com.example.musicfy.ui.screens.Screens
import com.example.musicfy.ui.screens.modules.ModuleSheetHost
import com.example.musicfy.ui.screens.navigationBuilder
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * KMP counterpart of upstream `AppNavigation.kt` / `AppNavigationBar`.
 *
 * The Android version used a Material3 Scaffold with Lottie tab icons and a
 * separate rail for tablets. Here the four tabs live in [GlassNavigationBar];
 * every destination still uses the same Compose route graph as upstream.
 */
@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route
    var selected by remember { mutableStateOf<Screens>(Screens.Home) }

    Box(modifier = Modifier.fillMaxSize()) {
        NavHost(
            navController = navController,
            startDestination = Screens.Home.route,
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = GlassNavHeight),
        ) {
            navigationBuilder(navController)
        }

        if (currentRoute == null || currentRoute in Routes.MainRoutes) {
            GlassNavigationBar(
                items = Screens.MainScreens,
                selected = selected,
                onSelect = { screen ->
                    selected = screen
                    navController.navigate(screen.route) {
                        popUpTo(Screens.Home.route) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
                label = { stringResource(it.title) },
                icon = { screen ->
                    Icon(
                        painter = painterResource(
                            if (screen == selected) screen.selectedIcon else screen.icon,
                        ),
                        contentDescription = null,
                        tint = if (screen == selected) Color.White else GlassTokens.MutedContent,
                        modifier = Modifier.size(22.dp),
                    )
                },
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        PlayerOverlay(
            navController = navController,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        ModuleSheetHost()
    }
}

/** Floating now-playing pill that opens the full glass player sheet. */
@Composable
private fun PlayerOverlay(navController: NavController, modifier: Modifier = Modifier) {
    val controller = LocalPlaybackController.current ?: return
    val uiState = LocalPlayerUiState.current ?: return
    val sheet = rememberPlayerSheetState()
    val track by uiState.trackInfo.collectAsState()
    val transport by uiState.transportState.collectAsState()
    val pureBlack by rememberPreference(SettingsKeys.PureBlackKey, false)

    if (sheet.expanded) {
        BottomSheetPlayer(
            state = sheet,
            navController = navController,
            modifier = Modifier.fillMaxSize(),
            pureBlack = pureBlack,
        )
        return
    }
    if (track.mediaId.isEmpty()) return

    Row(
        modifier = modifier
            .padding(bottom = GlassNavHeight + 8.dp)
            .padding(horizontal = 16.dp)
            .fillMaxWidth()
            .height(64.dp)
            .glassSurface(shape = RoundedCornerShape(GlassTokens.NavCorner))
            .clickable { sheet.expand() }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            painter = painterResource(if (transport.isPlaying) Res.drawable.pause else Res.drawable.play),
            contentDescription = null,
            tint = GlassTokens.Pink,
            modifier = Modifier.size(20.dp),
        )
        Spacer(Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = track.title.ifEmpty { stringResource(Res.string.now_playing) },
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = track.artist,
                color = GlassTokens.MutedContent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Icon(
            painter = painterResource(Res.drawable.expand_more),
            contentDescription = null,
            tint = GlassTokens.MutedContent,
            modifier = Modifier.size(18.dp),
        )
    }
}

private val GlassNavHeight = 104.dp
