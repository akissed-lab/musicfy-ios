package com.example.musicfy.ui.utils

import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.runtime.Composable
import androidx.compose.runtime.NonRestartableComposable
import androidx.compose.ui.unit.dp

/**
 * The system bars' insets as they are when shown - what screens should lay out against.
 *
 * The player hides the navigation bar while it's expanded. Every screen that read the live
 * `WindowInsets.systemBars` in composition recomposed through that bar's hide and show animation,
 * which runs at the end of every open and the start of every close - exactly when the screen is
 * being revealed behind the player. The bars' shown size never changes, so nothing has to.
 */
@OptIn(ExperimentalLayoutApi::class)
val WindowInsets.Companion.stableSystemBars: WindowInsets
    @Composable
    @NonRestartableComposable
    // Common-safe stand-in: no platform system-bar query exists in commonMain.
    get() = WindowInsets(left = 0.dp, top = 0.dp, right = 0.dp, bottom = 0.dp)
