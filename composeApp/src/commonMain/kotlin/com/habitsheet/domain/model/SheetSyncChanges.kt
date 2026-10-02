package com.habitsheet.domain.model

/**
 * Everything one sheet sync changes locally. A repository applies it all-or-nothing
 * (see `HabitRepository.applySheetSync`). [habitsToSave] holds new habits and renamed ones.
 */
data class SheetSyncChanges(
    val planIdsToDelete: List<String> = emptyList(),
    val habitsToSave: List<DailyHabit> = emptyList(),
    val plansToSave: List<DayPlan> = emptyList(),
    val completionsToSave: List<DailyHabitCompletion> = emptyList(),
    val managedHabitIds: Set<String>,
)
