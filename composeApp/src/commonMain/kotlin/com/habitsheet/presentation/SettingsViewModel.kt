package com.habitsheet.presentation

import com.habitsheet.domain.repository.HabitRepository
import com.habitsheet.domain.model.SheetLink
import com.habitsheet.sync.SheetSync
import com.habitsheet.sync.SheetSyncState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ThemeMode {
    System, Light, Dark
}

class SettingsViewModel(
    private val repository: HabitRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Default,
    private val sheetSync: SheetSync? = null,
) {
    private val _themeMode = MutableStateFlow(ThemeMode.System)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()
    private val _sheetUrl = MutableStateFlow("")
    val sheetUrl: StateFlow<String> = _sheetUrl.asStateFlow()
    private val _sheetMessage = MutableStateFlow<String?>(null)
    val sheetMessage: StateFlow<String?> = _sheetMessage.asStateFlow()
    val sheetSyncState: StateFlow<SheetSyncState> = sheetSync?.state ?: MutableStateFlow(SheetSyncState())

    init {
        scope.launch(ioDispatcher) {
            val mode = repository.getThemeMode()
            _themeMode.value = ThemeMode.entries.getOrElse(mode) { ThemeMode.System }
            _sheetUrl.value = repository.getSheetUrl()
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        scope.launch(ioDispatcher) {
            repository.setThemeMode(mode.ordinal)
        }
    }

    fun saveSheetUrl(input: String) {
        val canonical = if (input.isBlank()) "" else SheetLink.canonicalize(input)
        if (canonical == null) {
            _sheetMessage.value = "Enter a Google Sheets link."
            return
        }
        scope.launch(ioDispatcher) {
            try {
                repository.setSheetUrl(canonical)
                _sheetUrl.value = canonical
                _sheetMessage.value = if (canonical.isEmpty()) "Sheet link removed." else "Sheet link saved. Tap Connect & sync to authorize Google."
            } catch (_: Exception) {
                _sheetMessage.value = "Could not save the sheet link."
            }
        }
    }

    fun syncNow() {
        val service = sheetSync ?: return
        scope.launch(ioDispatcher) { service.sync(interactive = true) }
    }

    fun close() {
        scope.cancel()
    }
}
