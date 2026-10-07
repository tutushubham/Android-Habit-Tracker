package com.habitsheet.presentation

import com.habitsheet.domain.model.Category
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.DailyHabitCompletion
import com.habitsheet.domain.model.DayPlan
import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.domain.model.WeeklyHabit
import com.habitsheet.domain.model.WeeklyHabitCompletion
import com.habitsheet.domain.model.WeeklyPlan
import com.habitsheet.testing.English
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** The wording of every destructive confirmation, rendered from the English string catalog. */
class ConfirmationTextTest {
    private val day = LocalDate(2026, 8, 5)
    private val run = DailyHabit("run", "Morning Run", "c1", 10, 0, true, LocalDate(2026, 8, 1), null, 1, 1)
    private val gym = WeeklyHabit("gym", "Gym", "c1", 0, true, LocalDate(2026, 8, 1), null, 1, 1)
    private val snapshot = HabitSnapshot(
        categories = listOf(Category("c1", "Fitness", 0, true, 1)),
        dailyHabits = listOf(run),
        dailyCompletions = listOf(
            DailyHabitCompletion("run", day, true, 1),
            DailyHabitCompletion("run", day.plus(1, kotlinx.datetime.DateTimeUnit.DAY), true, 1),
            DailyHabitCompletion("run", day.plus(2, kotlinx.datetime.DateTimeUnit.DAY), false, 1),
        ),
        weeklyHabits = listOf(gym),
        weeklyCompletions = listOf(WeeklyHabitCompletion("gym", day, true, 1)),
        weeklyPlans = listOf(WeeklyPlan("run", 2, "Intervals", 1)),
        dayPlans = listOf(DayPlan("run", day, "Easy 6 km", false, 1)),
    )

    // ---- confirmation text names what is affected -------------------------------------------------

    @Test
    fun deletingAHabitNamesItAndWhatGoesWithIt() {
        val text = DestructiveAction.delete(run, snapshot).confirmation
        assertTrue("'Morning Run'" in English.render(text.message))
        assertTrue("2 check-offs" in English.render(text.message))
        assertTrue("2 planned sessions" in English.render(text.message))
        assertTrue("cannot be undone" in English.render(text.message) && "Archive" in English.render(text.message))
        assertEquals("Delete", English.render(text.confirmLabel))

        val weekly = DestructiveAction.delete(gym, snapshot).confirmation
        assertTrue("'Gym'" in English.render(weekly.message) && "1 check-off" in English.render(weekly.message), English.render(weekly.message))
    }

    @Test
    fun deletingACategoryNamesItAndSaysHabitsStay() {
        val text = DestructiveAction.delete(snapshot.categories.single(), snapshot).confirmation.message.let(English::render)
        assertTrue("'Fitness'" in text && "2 habits" in text && "stay" in text, text)
        assertTrue("No habits use it" in DestructiveAction.DeleteCategory("Empty", 0).confirmation.message.let(English::render))
    }

    @Test
    fun removingASessionNamesTheHabitAndTheSession() {
        val daily = DestructiveAction.RemoveDaySession("Morning Run", day, "Easy 6 km").confirmation.message.let(English::render)
        assertTrue("Easy 6 km" in daily && "Morning Run" in daily && "2026-08-05" in daily && "kept" in daily, daily)
        val weekly = DestructiveAction.RemoveWeeklySession("Morning Run", "Tuesday", "Intervals").confirmation.message.let(English::render)
        assertTrue("Intervals" in weekly && "every Tuesday" in weekly, weekly)
    }

    @Test
    fun resetTextSaysItIsLocalOnlyAndWhatItRemoves() {
        val text = DestructiveAction.ResetAllData(DataSummary.of(snapshot)).confirmation
        assertTrue("deletes 1 daily habit, 1 weekly habit and 3 check-offs on this device" in English.render(text.message))
        assertTrue("Your Google Sheet is untouched" in English.render(text.message))
        assertTrue("disconnects the sheet link" in English.render(text.message))
        assertTrue("cannot be undone" in English.render(text.message))
        assertTrue("no habits or history" in DestructiveAction.ResetAllData(DataSummary(0, 0, 0)).confirmation.message.let(English::render))
    }

    @Test
    fun startingWithEmptyDataSaysTheOldFileIsKeptAndTheSheetUntouched() {
        val text = DestructiveAction.SetAsideDamagedData.confirmation
        assertTrue("moves it aside" in English.render(text.message) && "kept on the device" in English.render(text.message))
        assertTrue("Google Sheet is untouched" in English.render(text.message) && "save a copy first" in English.render(text.message))
        assertEquals("Start with empty data", English.render(text.confirmLabel))
    }

    @Test
    fun importTextSaysWhatWillBeReplacedAndThatTheSheetIsUntouched() {
        val text = DestructiveAction.ReplaceWithBackup(DataSummary.of(snapshot)).confirmation.message.let(English::render)
        assertTrue("replaces 1 daily habit, 1 weekly habit and 3 check-offs" in text, text)
        assertTrue("Google Sheet is untouched" in text && "cannot be undone" in text, text)
    }
}
