// SettingsState.kt
//
// Compose helpers mirroring upstream `utils/DataStore.kt`. SettingsStore itself
// is synchronous; a coarse global version flow re-reads every remembered
// preference after any write so screens and the theme stay in sync.

package com.example.musicfy.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.update

/** Provide the app's SettingsStore above any screen using the helpers below. */
val LocalSettingsStore = compositionLocalOf<SettingsStore?> { null }

/**
 * SettingsStore is synchronous but has no observable flow yet. Every write bumps this version so
 * all rememberPreference call sites re-read the store and stay in sync. This is deliberately
 * coarse: a single preference change recomposes every remembered preference.
 */
private val SettingsChangeVersion = MutableStateFlow(0)

fun notifySettingsChanged() {
    SettingsChangeVersion.update { it + 1 }
}

@Composable
fun rememberSettingsVersion(): Int = SettingsChangeVersion.collectAsState().value

@Composable
fun ProvideSettingsStore(
    store: SettingsStore?,
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalSettingsStore provides store, content = content)
}

@Composable
private fun <T> rememberStoredPreference(
    key: String,
    read: (SettingsStore?) -> T,
    write: (SettingsStore?, T) -> Unit,
): MutableState<T> {
    val store = LocalSettingsStore.current
    val version = rememberSettingsVersion()
    // Recreate the remembered copy whenever any store write bumps the version.
    val state = remember(key, store, version) { mutableStateOf(read(store)) }
    return remember(key, store, state) {
        object : MutableState<T> {
            override var value: T
                get() = state.value
                set(value) {
                    write(store, value)
                    notifySettingsChanged()
                }

            override fun component1(): T = value

            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}

@Composable
fun rememberPreference(key: String, defaultValue: Boolean): MutableState<Boolean> =
    rememberStoredPreference(
        key = key,
        read = { it?.getBoolean(key, defaultValue) ?: defaultValue },
        write = { store, value -> store?.setBoolean(key, value) },
    )

@Composable
fun rememberPreference(key: String, defaultValue: Int): MutableState<Int> =
    rememberStoredPreference(
        key = key,
        read = { it?.getInt(key, defaultValue) ?: defaultValue },
        write = { store, value -> store?.setInt(key, value) },
    )

@Composable
fun rememberPreference(key: String, defaultValue: Long): MutableState<Long> =
    rememberStoredPreference(
        key = key,
        read = { it?.getLong(key, defaultValue) ?: defaultValue },
        write = { store, value -> store?.setLong(key, value) },
    )

@Composable
fun rememberPreference(key: String, defaultValue: Float): MutableState<Float> =
    rememberStoredPreference(
        key = key,
        read = { it?.getFloat(key, defaultValue) ?: defaultValue },
        write = { store, value -> store?.setFloat(key, value) },
    )

@Composable
fun rememberPreference(key: String, defaultValue: String): MutableState<String> =
    rememberStoredPreference(
        key = key,
        read = { it?.getString(key, defaultValue) ?: defaultValue },
        write = { store, value -> store?.setString(key, value) },
    )

/** Parses a stored enum name, falling back to [defaultValue] for missing/unknown values. */
inline fun <reified T : Enum<T>> String?.toEnum(defaultValue: T): T =
    if (this == null) {
        defaultValue
    } else {
        try {
            enumValueOf<T>(this)
        } catch (_: IllegalArgumentException) {
            defaultValue
        }
    }

@Composable
inline fun <reified T : Enum<T>> rememberEnumPreference(
    key: String,
    defaultValue: T,
): MutableState<T> {
    val store = LocalSettingsStore.current
    val version = rememberSettingsVersion()
    val state = remember(key, store, version) {
        mutableStateOf((store?.getString(key, "") ?: "").toEnum(defaultValue))
    }
    return remember(key, store, state) {
        object : MutableState<T> {
            override var value: T
                get() = state.value
                set(value) {
                    store?.setString(key, value.name)
                    notifySettingsChanged()
                }

            override fun component1(): T = value

            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}
