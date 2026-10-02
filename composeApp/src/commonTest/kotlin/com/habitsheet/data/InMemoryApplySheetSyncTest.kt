package com.habitsheet.data

import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.DayPlan
import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.domain.model.SheetSyncChanges
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails

class InMemoryApplySheetSyncTest {
    private val day = LocalDate(2026, 10, 1)
    private fun habit(id: String, name: String) = DailyHabit(id, name, null, 0, 0, true, day, null, 1, 1, datedOnly = true)

    @Test
    fun failureMidApplyLeavesStateKeysAndLastSyncUntouched() = runTest {
        val repo = InMemoryHabitRepository(HabitSnapshot(
            dailyHabits = listOf(habit("run", "Run")),
            dayPlans = listOf(DayPlan("run", day, "Easy", false, 1, "run-1")),
        ))
        repo.setSheetSyncedKeys(setOf("run-1"))
        repo.setSheetLastSync(100)
        val before = repo.snapshot.value

        assertFails {
            repo.applySheetSync(
                SheetSyncChanges(
                    planIdsToDelete = listOf("run-1"),
                    habitsToSave = listOf(habit("yoga", "Yoga")),
                    plansToSave = listOf(DayPlan("yoga", day, "Flow", false, 5, "yoga-1"), DayPlan("ghost", day, "Boo", false, 5, "g-1")),
                    managedHabitIds = setOf("yoga"),
                ),
                newKeys = setOf("yoga-1"),
                lastSync = 777,
            )
        }

        assertEquals(before, repo.snapshot.value)
        assertEquals(setOf("run-1"), repo.getSheetSyncedKeys())
        assertEquals(100, repo.getSheetLastSync())
    }
}
