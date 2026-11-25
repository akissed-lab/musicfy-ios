package com.example.musicfy.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontFamily
import com.example.musicfy.settings.SettingsKeys
import com.example.musicfy.settings.rememberPreference

/**
 * Portable subset of upstream PR #356's custom font mode.
 *
 * Only the three modes that map cleanly onto Compose Multiplatform are kept:
 * the bundled Inter family, the platform default family, and the platform
 * monospace family. Values are stored as enum names so a settings selector can
 * reuse [AppFontMode.name] directly.
 */
enum class AppFontMode {
    DEFAULT,
    SYSTEM,
    MONOSPACE;

    companion object {
        const val DefaultStoredValue = "DEFAULT"

        /** Unknown or missing values fall back to the original Inter typography. */
        fun fromStoredValue(value: String?): AppFontMode =
            entries.firstOrNull { it.name == value } ?: DEFAULT
    }
}

/**
 * Resolves the app-wide font family from the stored mode.
 *
 * Reads [SettingsKeys.CustomFontModeKey] (key `customFontMode`, default
 * `DEFAULT`) through the project's existing rememberPreference API, so the
 * Material typography follows the selection without any platform-specific
 * font loading.
 */
@Composable
fun rememberAppFontFamily(): FontFamily {
    val (storedMode) = rememberPreference(
        key = SettingsKeys.CustomFontModeKey,
        defaultValue = AppFontMode.DefaultStoredValue,
    )
    return when (AppFontMode.fromStoredValue(storedMode)) {
        AppFontMode.DEFAULT -> interFontFamily()
        AppFontMode.SYSTEM -> FontFamily.Default
        AppFontMode.MONOSPACE -> FontFamily.Monospace
    }
}
