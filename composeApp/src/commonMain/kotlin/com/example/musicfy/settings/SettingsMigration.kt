// SettingsMigration.kt
//
// Key sets and one-off cleanup helpers carried over from upstream. No existing
// key file is touched: the dynamic `playerStyleRotated_*` keys were never in
// SettingsKeys (they are generated per PlayerStyle), and the Discord legacy
// keys were removed from PreferenceKeys after the OAuth rewrite.

package com.example.musicfy.settings

import com.example.musicfy.constants.PlayerStyle

object SettingsMigration {
    /** The key upstream builds with `playerStyleRotatedKey(style)`. */
    fun playerStyleRotatedKey(style: PlayerStyle): String = "playerStyleRotated_${style.name}"

    /** All five dynamic rotation keys (one per player style). */
    val PlayerStyleRotatedKeys: List<String> = PlayerStyle.entries.map { playerStyleRotatedKey(it) }

    /**
     * Upstream DiscordAuth.LegacyKeys: eight strings and five booleans from the
     * old "read the account login token out of a web page" sign-in.
     */
    val DiscordLegacyKeys: List<String> = listOf(
        "discordToken", "discordUsername", "discordName", "discordAvatar", "discordStatus",
        "discordButton1Text", "discordButton2Text", "discordActivityName",
        "discordInfoDismissed", "discordUseDetails", "discordButton1Visible",
        "discordButton2Visible", "discordAdvancedMode",
    )

    fun getPlayerStyleRotated(
        store: SettingsStore,
        style: PlayerStyle,
        default: Boolean = false,
    ): Boolean = store.getBoolean(playerStyleRotatedKey(style), default)

    fun setPlayerStyleRotated(store: SettingsStore, style: PlayerStyle, value: Boolean) =
        store.setBoolean(playerStyleRotatedKey(style), value)

    /** Removes one style's rotation flag, or all five when [style] is null. */
    fun removePlayerStyleRotated(store: SettingsStore, style: PlayerStyle? = null) {
        if (style == null) store.removeAll(PlayerStyleRotatedKeys)
        else store.remove(playerStyleRotatedKey(style))
    }

    /**
     * Lands a stored choice from a removed player style on its nearest
     * successor, using the legacy mapping from `PlayerStyle.fromStored`.
     */
    fun migratePlayerStyle(store: SettingsStore) {
        val stored = store.getString(SettingsKeys.PlayerStyleKey, "")
        PlayerStyle.fromStored(stored)?.let { store.setString(SettingsKeys.PlayerStyleKey, it.name) }
    }

    /** Wipes the old Discord login material, leaving OAuth tokens untouched. */
    fun forgetDiscordLegacy(store: SettingsStore) = store.removeAll(DiscordLegacyKeys)

    /** Runs the one-off migrations. Safe to call on every start. */
    fun runAll(store: SettingsStore) {
        migratePlayerStyle(store)
        forgetDiscordLegacy(store)
    }
}
