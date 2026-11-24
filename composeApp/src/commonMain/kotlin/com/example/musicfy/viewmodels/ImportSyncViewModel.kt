package com.example.musicfy.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.musicfy.importer.ImportOptions
import com.example.musicfy.importer.ImportProgress
import com.example.musicfy.importer.LinkImporter
import com.example.musicfy.importer.MusicImportService
import com.example.musicfy.importer.ParsedImport
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** Backs the public-link import sheet: read a link, show what it found, then start the import. */
class ImportSyncViewModel(container: AppContainer) : ViewModel() {
    private val importer = MusicImportService(container)

    val progress: StateFlow<ImportProgress> = importer.progress

    private val _pending = MutableStateFlow<ParsedImport?>(null)
    val pending: StateFlow<ParsedImport?> = _pending.asStateFlow()

    private val _notice = MutableStateFlow<String?>(null)
    val notice: StateFlow<String?> = _notice.asStateFlow()

    fun loadLink(link: String) {
        viewModelScope.launch {
            _notice.value = null
            _pending.value = null
            if (!importer.isRunning) importer.clearFinished()
            try {
                _pending.value = LinkImporter.fetch(link)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _notice.value = e.message ?: "Couldn't read that link."
            }
        }
    }

    fun confirm() {
        val parsed = _pending.value ?: return
        if (importer.startImport(parsed, ImportOptions())) {
            _pending.value = null
            _notice.value = null
        } else {
            _notice.value = "An import is already running."
        }
    }

    /** Cancels a running import; with none running, clears the parsed link. */
    fun cancel() {
        if (importer.isRunning) {
            importer.cancel()
        } else {
            _pending.value = null
        }
    }

    /** Dismisses the message and any finished import summary. */
    fun clearNotice() {
        _notice.value = null
        if (!importer.isRunning) importer.clearFinished()
    }

    override fun onCleared() {
        importer.cancel()
        super.onCleared()
    }
}
