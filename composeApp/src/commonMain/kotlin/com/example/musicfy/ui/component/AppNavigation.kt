package com.example.musicfy.ui.component

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.musicfy.ui.screens.Routes
import com.example.musicfy.ui.screens.Screens
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
    }
}

private val GlassNavHeight = 104.dp
