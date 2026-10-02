package com.habitsheet.domain.model

import kotlinx.datetime.LocalDate

/** Proof that the completion with this exact [updatedAtEpochMillis] reached the sheet (or already matched it). */
data class CompletionAck(val planId: String, val date: LocalDate, val updatedAtEpochMillis: Long)

/**
 * Everything one sheet sync changes locally. A repository applies it all-or-nothing
 * (see `HabitRepository.applySheetSync`). [habitsToSave] holds new habits and renamed ones;
 * [completionsToSave] are values taken FROM the sheet and are written with the pending flag cleared.
 * [completionsToAcknowledge] clears the pending flag of completions whose current value is now in the sheet.
 */
data class SheetSyncChanges(
    val planIdsToDelete: List<String> = emptyList(),
    val habitsToSave: List<DailyHabit> = emptyList(),
    val plansToSave: List<DayPlan> = emptyList(),
    val completionsToSave: List<DailyHabitCompletion> = emptyList(),
    val completionsToAcknowledge: List<CompletionAck> = emptyList(),
    val managedHabitIds: Set<String>,
)
