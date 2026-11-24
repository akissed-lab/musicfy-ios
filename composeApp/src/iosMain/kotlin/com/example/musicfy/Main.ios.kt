package com.example.musicfy

import androidx.compose.ui.window.ComposeUIViewController

/**
 * iOS entry point. The only code in the Xcode project is a trivial Swift host
 * that calls this function; the entire UI lives in commonMain Kotlin/Compose.
 */
fun MainViewController() = ComposeUIViewController {
    MusicfyApp()
}
