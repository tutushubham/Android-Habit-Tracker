package com.habitsheet

import com.habitsheet.data.InMemoryHabitRepository
import com.habitsheet.domain.model.*
import com.habitsheet.presentation.BackupViewModel
import com.habitsheet.ui.BackupService
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.*

class ResetDataTest {

    @Test
    fun resetAllDataEndToEnd() = runTest {
        val repository = InMemoryHabitRepository()
        val backupService = object : BackupService {
            override fun exportBackup(json: String, onResult: (com.habitsheet.ui.BackupResult) -> Unit) {}
            override fun exportCsv(csv: String, onResult: (com.habitsheet.ui.BackupResult) -> Unit) {}
            override fun importBackup(onImport: (String) -> Unit, onFailure: (String) -> Unit) {}
        }
        val viewModel = BackupViewModel(repository, backupService, scope = backgroundScope, callbackDispatcher = kotlinx.coroutines.test.UnconfinedTestDispatcher(testScheduler))

        // 1. Setup data
        repository.saveCategory(Category("c1", "Cat", 0, true, 0))
        repository.saveDailyHabit(DailyHabit("d1", "H1", null, 10, 0, true, LocalDate(2026, 8, 1), null, 0, 0))
        repository.setDailyCompletion(DailyHabitCompletion("d1", LocalDate(2026, 8, 26), true, 0))

        assertTrue(repository.snapshot.value.categories.isNotEmpty())
        assertTrue(repository.snapshot.value.dailyHabits.isNotEmpty())
        assertTrue(repository.snapshot.value.dailyCompletions.isNotEmpty())

        // 2. Reset
        viewModel.clearAllData()

        // 3. Verify
        val snapshot = repository.snapshot.first { it.categories.isEmpty() }
        assertTrue(snapshot.categories.isEmpty())
        assertTrue(snapshot.dailyHabits.isEmpty())
        assertTrue(snapshot.dailyCompletions.isEmpty())
    }
}
