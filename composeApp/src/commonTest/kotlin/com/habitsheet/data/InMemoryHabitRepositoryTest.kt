package com.habitsheet.data

import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.DailyHabitCompletion
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class InMemoryHabitRepositoryTest {
    @Test
    fun completionPersistsAcrossRefreshAndMonthChanges() = runTest {
        val repository = InMemoryHabitRepository()
        val habit = DailyHabit(
            id = "read",
            name = "Read",
            categoryId = null,
            monthlyGoal = 20,
            displayOrder = 0,
            active = true,
            createdOn = LocalDate(2026, 8, 1),
            createdAtEpochMillis = 0,
            updatedAtEpochMillis = 0,
        )
        val august = DailyHabitCompletion("read", LocalDate(2026, 8, 5), true, 1)
        val september = DailyHabitCompletion("read", LocalDate(2026, 9, 5), true, 2)

        repository.saveDailyHabit(habit)
        repository.setDailyCompletion(august)
        repository.setDailyCompletion(september)
        repository.refresh()

        assertEquals(2, repository.snapshot.value.dailyCompletions.size)
        assertTrue(repository.snapshot.value.dailyCompletions.contains(august))
        assertTrue(repository.snapshot.value.dailyCompletions.contains(september))
    }
}
