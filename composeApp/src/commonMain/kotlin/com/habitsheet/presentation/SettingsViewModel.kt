package com.habitsheet.presentation

import com.habitsheet.domain.repository.HabitRepository
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
    private val ioDispatcher: kotlinx.coroutines.CoroutineDispatcher = Dispatchers.Default
) {
    private val _themeMode = MutableStateFlow(ThemeMode.System)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    init {
        scope.launch(ioDispatcher) {
            val mode = repository.getThemeMode()
            _themeMode.value = ThemeMode.entries.getOrElse(mode) { ThemeMode.System }
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        _themeMode.value = mode
        scope.launch(ioDispatcher) {
            repository.setThemeMode(mode.ordinal)
        }
    }

    fun close() {
        scope.cancel()
    }
}
