// OfflineMode.kt

package com.example.musicfy.utils

import com.music.innertube.YouTube

/** KMP port of upstream `utils/OfflineMode.kt`. */
object OfflineMode {
    var isOn: Boolean = false
        private set

    fun set(on: Boolean) {
        isOn = on
        YouTube.offline = on
    }
}
