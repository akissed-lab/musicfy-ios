package com.example.musicfy

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import coil3.ImageLoader
import coil3.compose.setSingletonImageLoaderFactory
import com.example.musicfy.ui.component.GlassCard
import com.example.musicfy.ui.component.GlassNavigationBar
import com.example.musicfy.ui.component.GlassRoot
import com.example.musicfy.ui.screens.Screens
import com.example.musicfy.ui.theme.MusicfyTheme
import org.jetbrains.compose.resources.stringResource

/**
 * Root of the iOS application. Every screen is Kotlin/Compose shared code;
 * iosApp contains only the UIViewController bootstrap.
 *
 * The whole app lives inside [GlassRoot] so every ported screen inherits the
 * Apple Music-style liquid glass backdrop, chrome and card language. Screen
 * bodies are replaced with the ported originals stage by stage.
 */
@Composable
fun MusicfyApp() {
    // Same loader as upstream Coil 3, with the Darwin Ktor engine from iosMain.
    setSingletonImageLoaderFactory { context ->
        ImageLoader.Builder(context).build()
    }

    MusicfyTheme {
        GlassRoot {
            var screen by remember { mutableStateOf<Screens>(Screens.Home) }

            Box(modifier = Modifier.fillMaxSize()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 104.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    GlassCard {
                        Text(text = "${stringResource(screen.title)} (port in progress)")
                    }
                }

                GlassNavigationBar(
                    items = Screens.MainScreens,
                    selected = screen,
                    onSelect = { screen = it },
                    label = { stringResource(it.title) },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}
