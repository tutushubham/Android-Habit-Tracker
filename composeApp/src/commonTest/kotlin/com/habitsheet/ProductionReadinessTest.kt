package com.habitsheet

import com.habitsheet.data.DefaultIdGenerator
import com.habitsheet.data.InMemoryHabitRepository
import com.habitsheet.domain.backup.BackupSerializer
import com.habitsheet.domain.backup.CsvGenerator
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.domain.model.MonthKey
import com.habitsheet.presentation.BackupViewModel
import com.habitsheet.presentation.ManageHabitsViewModel
import com.habitsheet.presentation.MonthViewModel
import com.habitsheet.presentation.UiText
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.*

class ProductionReadinessTest {
    private val repository = InMemoryHabitRepository()
    private val idGenerator = DefaultIdGenerator()
    private val fixedDate = LocalDate(2026, 8, 27)
    private val dateProvider = object : com.habitsheet.presentation.DateProvider {
        override fun today(): LocalDate = fixedDate
        override fun nowEpochMillis(): Long = 1000
    }

    @Test
    fun fullAppLifecycleScenario() = runTest {
        // 1. Initialize ViewModels
        val monthViewModel = MonthViewModel(repository, repository, dateProvider, backgroundScope)
        val manageViewModel = ManageHabitsViewModel(repository, idGenerator, dateProvider, backgroundScope)
        val backupViewModel = BackupViewModel(
            repository,
            object : com.habitsheet.ui.BackupService {
                override fun exportBackup(json: String, onResult: (com.habitsheet.ui.BackupResult) -> Unit) {}
                override fun exportCsv(csv: String, onResult: (com.habitsheet.ui.BackupResult) -> Unit) {}
                override fun importBackup(onImport: (String) -> Unit, onFailure: (UiText) -> Unit) {}
            },
            dateProvider,
            backgroundScope,
        )

        // 2. Create categories and habits
        manageViewModel.addCategory("Fitness")
        val categoryId = repository.snapshot.first { it.categories.isNotEmpty() }.categories.first().id

        manageViewModel.addDailyHabit("Run", categoryId, 15)
        manageViewModel.addWeeklyHabit("Gym", categoryId)

        val snapshot = repository.snapshot.first { it.dailyHabits.isNotEmpty() && it.weeklyHabits.isNotEmpty() }
        val dailyHabitId = snapshot.dailyHabits.first().id
        val weeklyHabitId = snapshot.weeklyHabits.first().id

        // 3. Complete habits
        monthViewModel.toggleDaily(dailyHabitId, fixedDate)
        monthViewModel.toggleWeekly(weeklyHabitId, fixedDate) // week start date

        // 4. Verify data in state
        val state = monthViewModel.state.first { it.dailyCompletionKeys.isNotEmpty() }
        assertEquals(1, state.todaySummary.completedCount)
        assertEquals(1, state.todaySummary.totalCount)
        assertEquals(1.0, state.todaySummary.percentage)

        // 5. Navigate months
        monthViewModel.previousMonth()
        val statePrev = monthViewModel.state.first { it.selectedMonth.month == 7 }
        assertEquals(MonthKey(2026, 7), statePrev.selectedMonth)

        // 6. Archive and Restore
        manageViewModel.archiveDailyHabit(dailyHabitId)
        repository.snapshot.first { !it.dailyHabits.first().active }

        manageViewModel.restoreDailyHabit(dailyHabitId)
        repository.snapshot.first { it.dailyHabits.first().active }

        // 7. Backup and Export
        val finalSnapshot = repository.snapshot.value
        val json = BackupSerializer.serialize(finalSnapshot, 2000)
        val csv = CsvGenerator.generate(finalSnapshot)

        assertTrue(json.contains("Run"))
        assertTrue(csv.contains("Run"))
        assertTrue(csv.contains("Fitness"))

        // 8. Restore from backup
        val emptyRepo = InMemoryHabitRepository()
        emptyRepo.restoreFromSnapshot(BackupSerializer.deserialize(json))
        assertEquals(finalSnapshot.dailyHabits, emptyRepo.snapshot.value.dailyHabits)
        assertEquals(finalSnapshot.dailyCompletions, emptyRepo.snapshot.value.dailyCompletions)
    }
}
