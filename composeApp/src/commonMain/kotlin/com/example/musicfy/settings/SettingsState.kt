// SettingsState.kt
//
// Compose helpers mirroring upstream `utils/DataStore.kt`. SettingsStore has no
// reactive flow yet, so every preference is a locally remembered MutableState
// seeded from the store and written through on change. TODO: subscribe to a
// store flow so two screens showing the same key stay in sync.

package com.example.musicfy.settings

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember

/** Provide the app's SettingsStore above any screen using the helpers below. */
val LocalSettingsStore = compositionLocalOf<SettingsStore?> { null }

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
    // TODO: derive this from a SettingsStore data flow instead of remembering a copy.
    val state = remember(key, store) { mutableStateOf(read(store)) }
    return remember(key, store, state) {
        object : MutableState<T> {
            override var value: T
                get() = state.value
                set(value) {
                    state.value = value
                    write(store, value)
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
    // TODO: derive this from a SettingsStore data flow instead of remembering a copy.
    val state = remember(key, store) {
        mutableStateOf((store?.getString(key, "") ?: "").toEnum(defaultValue))
    }
    return remember(key, store, state) {
        object : MutableState<T> {
            override var value: T
                get() = state.value
                set(value) {
                    state.value = value
                    store?.setString(key, value.name)
                }

            override fun component1(): T = value

            override fun component2(): (T) -> Unit = { value = it }
        }
    }
}
