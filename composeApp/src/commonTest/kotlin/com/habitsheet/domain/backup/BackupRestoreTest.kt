package com.habitsheet.domain.backup

import com.habitsheet.data.InMemoryHabitRepository
import com.habitsheet.domain.model.*
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.*

class BackupRestoreTest {

    @Test
    fun completeRoundTripPreservesAllData() = runTest {
        val repository = InMemoryHabitRepository()
        
        // 1. Setup initial data
        val category = Category("cat1", "Fitness", 0, true, 1000)
        repository.saveCategory(category)
        
        val dailyHabit = DailyHabit(
            "d1", "Run", category.id, 10, 0, true, 
            LocalDate(2026, 8, 1), null, 1000, 1000
        )
        repository.saveDailyHabit(dailyHabit)
        repository.setDailyCompletion(DailyHabitCompletion(dailyHabit.id, LocalDate(2026, 8, 5), true, 1000))
        
        val archivedHabit = DailyHabit(
            "d2", "Old", null, 5, 1, false,
            LocalDate(2026, 7, 1), LocalDate(2026, 8, 1), 500, 1200
        )
        repository.saveDailyHabit(archivedHabit)

        val weeklyHabit = WeeklyHabit(
            "w1", "Gym", null, 0, true,
            LocalDate(2026, 8, 1), null, 1000, 1000
        )
        repository.saveWeeklyHabit(weeklyHabit)
        repository.setWeeklyCompletion(WeeklyHabitCompletion(weeklyHabit.id, LocalDate(2026, 8, 3), true, 1000))

        val originalSnapshot = repository.snapshot.value

        // 2. Export
        val json = BackupSerializer.serialize(originalSnapshot, 2000)
        
        // 3. Clear repository
        val emptyRepo = InMemoryHabitRepository()
        
        // 4. Import
        val restoredSnapshot = BackupSerializer.deserialize(json)
        emptyRepo.restoreFromSnapshot(restoredSnapshot)
        
        val finalSnapshot = emptyRepo.snapshot.value

        // 5. Compare
        assertEquals(originalSnapshot.categories, finalSnapshot.categories)
        assertEquals(originalSnapshot.dailyHabits, finalSnapshot.dailyHabits)
        assertEquals(originalSnapshot.dailyCompletions, finalSnapshot.dailyCompletions)
        assertEquals(originalSnapshot.weeklyHabits, finalSnapshot.weeklyHabits)
        assertEquals(originalSnapshot.weeklyCompletions, finalSnapshot.weeklyCompletions)
        
        // Verify archived habit preserved
        val restoredArchived = finalSnapshot.dailyHabits.find { it.id == "d2" }
        assertNotNull(restoredArchived)
        assertFalse(restoredArchived.active)
        assertEquals(LocalDate(2026, 8, 1), restoredArchived.archivedOn)
    }

    @Test
    fun deserializeThrowsOnUnsupportedVersion() {
        val malformedJson = """{"version": 2, "timestamp": 0, "data": {}}"""
        assertFailsWith<IllegalArgumentException> {
            BackupSerializer.deserialize(malformedJson)
        }
    }

    @Test
    fun deserializeThrowsOnInvalidJson() {
        assertFails {
            BackupSerializer.deserialize("not json")
        }
    }
}
