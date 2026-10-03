package com.habitsheet.presentation

import com.habitsheet.domain.repository.HabitRepository
import com.habitsheet.domain.model.SheetLink
import com.habitsheet.platform.Logger
import com.habitsheet.platform.NoOpLogger
import com.habitsheet.platform.e
import com.habitsheet.platform.runCatchingCancellable
import com.habitsheet.sync.SheetSync
import com.habitsheet.sync.SheetSyncState
import com.habitsheet.sync.SyncError
import com.habitsheet.sync.userMessage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/** What the settings screen shows about the last sync. [isError] is false for "Will sync when online." */
data class SyncStatus(val text: String, val isError: Boolean)

internal fun SheetSyncState.toStatus(): SyncStatus = when (val failure = error) {
    null -> SyncStatus(message, isError = false)
    is SyncError.Offline -> SyncStatus(failure.userMessage(), isError = false)
    else -> SyncStatus(failure.userMessage(), isError = true)
}

enum class ThemeMode {
    System, Light, Dark
}

class SettingsViewModel(
    private val repository: HabitRepository,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Default,
    private val sheetSync: SheetSync? = null,
    private val logger: Logger = NoOpLogger,
) {
    private val _themeMode = MutableStateFlow(ThemeMode.System)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()
    private val _sheetUrl = MutableStateFlow("")
    val sheetUrl: StateFlow<String> = _sheetUrl.asStateFlow()
    private val _sheetMessage = MutableStateFlow<String?>(null)
    val sheetMessage: StateFlow<String?> = _sheetMessage.asStateFlow()
    val sheetSyncState: StateFlow<SheetSyncState> = sheetSync?.state ?: MutableStateFlow(SheetSyncState())
    val sheetSyncStatus: StateFlow<SyncStatus> = sheetSyncState.map { it.toStatus() }
        .stateIn(scope, SharingStarted.Eagerly, sheetSyncState.value.toStatus())

    init {
        scope.launch(ioDispatcher) {
            runCatchingCancellable {
                val mode = repository.getThemeMode()
                _themeMode.value = ThemeMode.entries.getOrElse(mode) { ThemeMode.System }
                _sheetUrl.value = repository.getSheetUrl()
            }.onFailure { logger.e(TAG, "loading settings failed", it) }
        }
    }

    /** Re-reads theme and sheet link and forgets the last sync result, after data was reset or restored. */
    fun reloadFromStorage() {
        scope.launch(ioDispatcher) {
            runCatchingCancellable {
                val mode = repository.getThemeMode()
                _themeMode.value = ThemeMode.entries.getOrElse(mode) { ThemeMode.System }
                _sheetUrl.value = repository.getSheetUrl()
                sheetSync?.reset()
            }.onFailure { logger.e(TAG, "reloadFromStorage failed", it) }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        scope.launch(ioDispatcher) {
            runCatchingCancellable { repository.setThemeMode(mode.ordinal) }
                .onFailure { logger.e(TAG, "setThemeMode failed", it) }
        }
    }

    fun saveSheetUrl(input: String) {
        val canonical = if (input.isBlank()) "" else SheetLink.canonicalize(input)
        if (canonical == null) {
            _sheetMessage.value = "Enter a Google Sheets link."
            return
        }
        scope.launch(ioDispatcher) {
            runCatchingCancellable {
                repository.setSheetUrl(canonical)
                _sheetUrl.value = canonical
                _sheetMessage.value = if (canonical.isEmpty()) "Sheet link removed." else "Sheet link saved. Tap Connect & sync to authorize Google."
            }.onFailure {
                logger.e(TAG, "saveSheetUrl failed", it)
                _sheetMessage.value = "Could not save the sheet link."
            }
        }
    }

    /** Unlinks the sheet and forgets sync state. Habits, plans and check-offs on this device are kept. */
    fun disconnect() {
        scope.launch(ioDispatcher) {
            runCatchingCancellable {
                repository.setSheetUrl("")
                _sheetUrl.value = ""
                sheetSync?.reset()
                _sheetMessage.value = "Disconnected. Your habits and check-offs stay on this device."
            }.onFailure {
                logger.e(TAG, "disconnect failed", it)
                _sheetMessage.value = "Could not disconnect. Please try again."
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

    private companion object {
        const val TAG = "Settings"
    }
}
