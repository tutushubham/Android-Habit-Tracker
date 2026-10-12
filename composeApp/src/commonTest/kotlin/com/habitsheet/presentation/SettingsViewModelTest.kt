package com.habitsheet.presentation

import com.habitsheet.data.InMemoryHabitRepository
import com.habitsheet.testing.English
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

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

    @Test
    fun connectRejectsALinkThatIsNotAGoogleSheet() = runTest {
        val viewModel = SettingsViewModel(repository, backgroundScope, UnconfinedTestDispatcher(testScheduler))

        viewModel.connect("https://example.com/spreadsheets/d/abc")

        assertEquals("", repository.getSheetUrl())
        assertEquals("Enter a Google Sheets link.", English.render(viewModel.sheetMessage.value!!))
    }

    @Test
    fun connectSavesTheCanonicalLinkWithoutAPriorSaveStep() = runTest {
        val viewModel = SettingsViewModel(repository, backgroundScope, UnconfinedTestDispatcher(testScheduler))

        viewModel.connect("  https://docs.google.com/spreadsheets/d/abc_123/edit#gid=0 ")

        assertEquals("https://docs.google.com/spreadsheets/d/abc_123/edit", repository.getSheetUrl())
        assertEquals("https://docs.google.com/spreadsheets/d/abc_123/edit", viewModel.sheetUrl.value)
    }
}
