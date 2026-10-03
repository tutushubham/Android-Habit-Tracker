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
        repository.saveWeeklyPlan(WeeklyPlan(dailyHabit.id, 2, "Intervals · 5 km", 1000))
        repository.saveDayPlan(DayPlan(dailyHabit.id, LocalDate(2026, 8, 5), "Easy run · 6 km", false, 1000))
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
        assertEquals(originalSnapshot.weeklyPlans, finalSnapshot.weeklyPlans)
        assertEquals(originalSnapshot.dayPlans, finalSnapshot.dayPlans)
        
        // Verify archived habit preserved
        val restoredArchived = finalSnapshot.dailyHabits.find { it.id == "d2" }
        assertNotNull(restoredArchived)
        assertFalse(restoredArchived.active)
        assertEquals(LocalDate(2026, 8, 1), restoredArchived.archivedOn)
    }

    @Test
    fun deserializeThrowsOnUnsupportedVersion() {
        val malformedJson = """{"version": 5, "timestamp": 0, "data": {}}"""
        assertFailsWith<BackupValidationException> {
            BackupSerializer.deserialize(malformedJson)
        }
    }

    @Test
    fun deserializeRejectsOldOrInvalidVersion() {
        val malformedJson = """{"version": 0, "timestamp": 0, "data": {}}"""
        assertFailsWith<BackupValidationException> {
            BackupSerializer.deserialize(malformedJson)
        }
    }

    @Test
    fun deserializeAcceptsUnknownFieldsFromCompatibleVersion() {
        val json = """
            {
              "version": 1,
              "timestamp": 10,
              "futureContainerField": "ignored",
              "data": { "futureSnapshotField": true }
            }
        """.trimIndent()

        assertEquals(HabitSnapshot(), BackupSerializer.deserialize(json))
    }

    @Test
    fun deserializeRejectsOrphanedCompletion() {
        val invalidSnapshot = HabitSnapshot(
            dailyCompletions = listOf(
                DailyHabitCompletion("missing", LocalDate(2026, 8, 5), true, 1000),
            ),
        )

        val error = assertFailsWith<BackupValidationException> {
            BackupSerializer.deserialize(BackupSerializer.serialize(invalidSnapshot, 2000))
        }

        assertTrue(error.message.orEmpty().contains("missing habit"))
    }

    @Test
    fun deserializeRejectsDuplicateEntityIdsAndCompletionKeys() {
        val habit = DailyHabit(
            "d1", "Run", null, 10, 0, true,
            LocalDate(2026, 8, 1), null, 1000, 1000,
        )
        val duplicateHabitSnapshot = HabitSnapshot(dailyHabits = listOf(habit, habit.copy(name = "Walk")))
        assertFailsWith<BackupValidationException> {
            BackupSerializer.deserialize(BackupSerializer.serialize(duplicateHabitSnapshot, 2000))
        }

        val completion = DailyHabitCompletion("d1", LocalDate(2026, 8, 5), true, 1000)
        val duplicateCompletionSnapshot = HabitSnapshot(
            dailyHabits = listOf(habit),
            dailyCompletions = listOf(completion, completion.copy(completed = false)),
        )
        assertFailsWith<BackupValidationException> {
            BackupSerializer.deserialize(BackupSerializer.serialize(duplicateCompletionSnapshot, 2000))
        }
    }

    @Test
    fun repositoryRejectsInvalidRestoreWithoutReplacingCurrentData() = runTest {
        val original = HabitSnapshot(
            categories = listOf(Category("cat1", "Fitness", 0, true, 1000)),
        )
        val repository = InMemoryHabitRepository(original)
        val invalid = HabitSnapshot(
            weeklyCompletions = listOf(
                WeeklyHabitCompletion("missing", LocalDate(2026, 8, 1), true, 1000),
            ),
        )

        assertFailsWith<BackupValidationException> {
            repository.restoreFromSnapshot(invalid)
        }
        assertEquals(original, repository.snapshot.value)
    }

    @Test
    fun deserializeRejectsEmptyFile() {
        assertFailsWith<BackupValidationException> {
            BackupSerializer.deserialize("  \n ")
        }
    }

    @Test
    fun deserializeThrowsOnInvalidJson() {
        assertFails {
            BackupSerializer.deserialize("not json")
        }
    }
}
