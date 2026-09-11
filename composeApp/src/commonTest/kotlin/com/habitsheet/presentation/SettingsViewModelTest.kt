package com.habitsheet.presentation

import com.habitsheet.data.InMemoryHabitRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsViewModelTest {
    private val repository = InMemoryHabitRepository()

    @Test
    fun initialThemeIsSystem() = runTest {
        val viewModel = SettingsViewModel(repository, backgroundScope, UnconfinedTestDispatcher(testScheduler))
        assertEquals(ThemeMode.System, viewModel.themeMode.first())
    }

    @Test
    fun changeThemeAndPersist() = runTest {
        val dispatcher = UnconfinedTestDispatcher(testScheduler)
        val viewModel = SettingsViewModel(repository, backgroundScope, dispatcher)
        
        viewModel.setThemeMode(ThemeMode.Dark)
        assertEquals(ThemeMode.Dark, viewModel.themeMode.first())
        
        // Verify it was saved to repository
        assertEquals(ThemeMode.Dark.ordinal, repository.getThemeMode())
        
        // Create new viewmodel to verify persistence
        val nextViewModel = SettingsViewModel(repository, backgroundScope, dispatcher)
        assertEquals(ThemeMode.Dark, nextViewModel.themeMode.first())
    }
}
