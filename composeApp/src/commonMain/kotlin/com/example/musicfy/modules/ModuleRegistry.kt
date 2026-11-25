// ModuleRegistry.kt
//
// iOS-safe counterpart of upstream `modules/ModuleRegistry.kt`. Upstream finds
// module apps by signature, unzips manifests and signed APKs, loads code with a
// class loader, probes secrets and applies a private-network URL guard. None of
// that exists here: registration is an in-memory list of data/conposable
// contributors. Call registration from app startup, before the first sheet opens.
//
// StateFlow updates are atomic; ordinary registration happens once at startup.

package com.example.musicfy.modules

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

object ModuleRegistry {
    private val _entries = MutableStateFlow(ModuleBuiltIns.registrations)

    /** Every module the UI can show, including the built-in ones. */
    val entries: StateFlow<List<ModuleRegistration>> get() = _entries

    /** Adds a module, or returns false when its id is already taken. */
    fun register(registration: ModuleRegistration): Boolean {
        if (_entries.value.any { it.descriptor.id == registration.moduleId }) return false
        _entries.update { it + registration }
        return true
    }

    fun registerLyrics(descriptor: ModuleDescriptor, provider: ModuleLyricsProvider): Boolean =
        register(
            ModuleRegistration(
                descriptor = descriptor.copy(capabilities = descriptor.capabilities + ModuleCapability.Lyrics),
                lyricsProvider = provider,
            ),
        )

    fun registerQueue(descriptor: ModuleDescriptor, provider: ModuleQueueProvider): Boolean =
        register(
            ModuleRegistration(
                descriptor = descriptor.copy(capabilities = descriptor.capabilities + ModuleCapability.Queue),
                queueProvider = provider,
            ),
        )

    fun registerSongInfo(descriptor: ModuleDescriptor, contributor: ModuleSongInfoContributor): Boolean =
        register(
            ModuleRegistration(
                descriptor = descriptor.copy(capabilities = descriptor.capabilities + ModuleCapability.SongInfo),
                songInfoContributor = contributor,
            ),
        )

    fun registerSheets(descriptor: ModuleDescriptor, sheets: List<ModuleSheet>): Boolean =
        register(
            ModuleRegistration(
                descriptor = descriptor.copy(capabilities = descriptor.capabilities + ModuleCapability.Sheets),
                sheets = sheets,
            ),
        )

    /** Removes a module by id. Built-in modules can be removed too; [reset] restores them. */
    fun unregister(moduleId: String) {
        _entries.update { current -> current.filterNot { it.moduleId == moduleId } }
    }

    /** Restores exactly the built-in modules. */
    fun reset() {
        _entries.value = ModuleBuiltIns.registrations
    }

    fun setEnabled(moduleId: String, enabled: Boolean) {
        _entries.update { current ->
            current.map { if (it.moduleId == moduleId) it.copy(enabled = enabled) else it }
        }
    }

    fun registration(moduleId: String): ModuleRegistration? =
        _entries.value.firstOrNull { it.moduleId == moduleId }

    fun descriptor(moduleId: String): ModuleDescriptor? = registration(moduleId)?.descriptor

    fun isEnabled(moduleId: String): Boolean = registration(moduleId)?.enabled == true

    /** Sheets of enabled modules, optionally only those of one module. */
    fun sheets(moduleId: String? = null): List<ModuleSheet> =
        _entries.value
            .filter { it.enabled && (moduleId == null || it.moduleId == moduleId) }
            .flatMap { it.sheets }

    fun findSheet(moduleId: String, sheetId: String): ModuleSheet? =
        registration(moduleId)
            ?.takeIf { it.enabled }
            ?.sheets
            ?.firstOrNull { it.id == sheetId }

    /**
     * Provider to use for a page. With no id, the first enabled module that
     * declares the capability wins; with an id, only that module is considered.
     */
    fun lyricsProvider(moduleId: String? = null): ModuleLyricsProvider? =
        provider(moduleId, ModuleCapability.Lyrics) { it.lyricsProvider }

    fun queueProvider(moduleId: String? = null): ModuleQueueProvider? =
        provider(moduleId, ModuleCapability.Queue) { it.queueProvider }

    fun songInfoContributors(moduleId: String? = null): List<ModuleSongInfoContributor> =
        _entries.value
            .filter { it.enabled && ModuleCapability.SongInfo in it.descriptor.capabilities }
            .filter { moduleId == null || it.moduleId == moduleId }
            .mapNotNull { it.songInfoContributor }

    private fun <T> provider(
        moduleId: String?,
        capability: ModuleCapability,
        select: (ModuleRegistration) -> T?,
    ): T? = _entries.value
        .filter { it.enabled && capability in it.descriptor.capabilities }
        .filter { moduleId == null || it.moduleId == moduleId }
        .firstNotNullOfOrNull(select)
}
