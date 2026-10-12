package com.habitsheet.domain.model

import com.habitsheet.testing.PROPERTY_RUNS
import com.habitsheet.testing.plusDays
import com.habitsheet.testing.randomSnapshot
import kotlinx.datetime.LocalDate
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Randomised checks of `plannedHabitsOn` against the rule written out in its documentation:
 * dated rows, else the weekday's weekly row, else every day only for a habit with no schedule at all.
 */
class PlanResolverPropertyTest {
    private data class Expected(val habitId: String, val id: String, val detail: String?, val skipped: Boolean)

    /** The documented precedence, written without sharing code with the implementation. */
    private fun expected(snapshot: HabitSnapshot, date: LocalDate): List<Expected> {
        val weekday = date.dayOfWeek.ordinal + 1 // Monday = 1
        val result = mutableListOf<Expected>()
        for (habit in snapshot.dailyHabits.sortedBy { it.displayOrder }) {
            val started = date >= habit.createdOn
            val ended = habit.archivedOn != null && date >= habit.archivedOn
            val live = habit.active || habit.archivedOn != null
            if (!started || ended || !live) continue

            val dated = snapshot.dayPlans.filter { it.habitId == habit.id && it.date == date }
            val weekly = snapshot.weeklyPlans.firstOrNull { it.habitId == habit.id && it.weekday == weekday }
            val hasAnySchedule = habit.datedOnly || snapshot.weeklyPlans.any { it.habitId == habit.id } || habit.id in snapshot.sheetManagedHabitIds
            when {
                dated.isNotEmpty() -> dated.forEach { result += Expected(habit.id, it.id, it.detail, it.skipped) }
                weekly != null -> result += Expected(habit.id, "${habit.id}|$date", weekly.detail, false)
                hasAnySchedule -> Unit
                else -> result += Expected(habit.id, "${habit.id}|$date", null, false)
            }
        }
        return result
    }

    private fun actual(snapshot: HabitSnapshot, date: LocalDate) =
        snapshot.plannedHabitsOn(date).map { Expected(it.habit.id, it.id, it.detail, it.skipped) }

    private fun forEachSeed(body: (seed: Int, random: Random, snapshot: HabitSnapshot, month: MonthKey) -> Unit) {
        repeat(PROPERTY_RUNS) { seed ->
            val random = Random(seed)
            val month = MonthKey(2024 + random.nextInt(3), random.nextInt(1, 13))
            body(seed, random, randomSnapshot(random, month), month)
        }
    }

    @Test
    fun everyDayOfEveryRandomSnapshotFollowsTheDocumentedPrecedence() = forEachSeed { seed, _, snapshot, month ->
        // A little before and after the month too: habits start, end and archive around its edges.
        for (offset in -3 until month.daysInMonth + 3) {
            val date = month.firstDay.plusDays(offset)
            assertEquals(expected(snapshot, date), actual(snapshot, date), "seed $seed, $date")
        }
    }

    @Test
    fun aDatedRowReplacesTheWeeklyAndDefaultResultOfItsHabitAndDateAndNothingElse() = forEachSeed { seed, random, snapshot, month ->
        val habit = snapshot.dailyHabits.random(random)
        val date = month.firstDay.plusDays(random.nextInt(0, month.daysInMonth))
        val withRow = snapshot.copy(dayPlans = snapshot.dayPlans + DayPlan(habit.id, date, "new row", false, 1, "new|$seed"))

        for (offset in 0 until month.daysInMonth) {
            val day = month.firstDay.plusDays(offset)
            val before = actual(snapshot, day)
            val after = actual(withRow, day)
            if (day != date) {
                assertEquals(before, after, "seed $seed: a row for $date changed $day")
            } else {
                assertEquals(before.filter { it.habitId != habit.id }, after.filter { it.habitId != habit.id }, "seed $seed: other habits on $date")
                if (habit.isActiveOn(date)) {
                    val mine = after.filter { it.habitId == habit.id }
                    val earlierRows = snapshot.dayPlans.filter { it.habitId == habit.id && it.date == date }.map { it.id }
                    assertEquals(earlierRows + "new|$seed", mine.map { it.id }, "seed $seed: dated rows win, in order")
                }
            }
        }
    }

    @Test
    fun aWeeklyRowOnlyAffectsItsWeekdayAndOnlyWhenNoDatedRowExists() = forEachSeed { seed, random, snapshot, month ->
        val habit = snapshot.dailyHabits.random(random)
        val weekday = random.nextInt(1, 8)
        val without = snapshot.copy(weeklyPlans = snapshot.weeklyPlans.filterNot { it.habitId == habit.id && it.weekday == weekday })
        val with = without.copy(weeklyPlans = without.weeklyPlans + WeeklyPlan(habit.id, weekday, "added", 1))
        // Adding the first weekly row also turns "every day" into "scheduled", which changes the other weekdays.
        val becameScheduled = without.weeklyPlans.none { it.habitId == habit.id } && !habit.datedOnly && habit.id !in snapshot.sheetManagedHabitIds

        for (date in month.dates()) {
            val hasDated = snapshot.dayPlans.any { it.habitId == habit.id && it.date == date }
            val affected = date.dayOfWeek.ordinal + 1 == weekday && !hasDated && habit.isActiveOn(date)
            val changed = actual(with, date).filter { it.habitId == habit.id } != actual(without, date).filter { it.habitId == habit.id }
            if (!affected && !becameScheduled) assertTrue(!changed, "seed $seed: a weekday-$weekday row changed $date")
            if (affected) assertEquals("added", actual(with, date).single { it.habitId == habit.id }.detail, "seed $seed, $date")
        }
    }

    @Test
    fun noHabitIsPlannedBeforeItIsCreatedOrOnAndAfterItsArchiveDay() = forEachSeed { seed, _, snapshot, month ->
        for (offset in -3 until month.daysInMonth + 3) {
            val date = month.firstDay.plusDays(offset)
            snapshot.plannedHabitsOn(date).forEach { planned ->
                val habit = planned.habit
                assertTrue(date >= habit.createdOn, "seed $seed: ${habit.id} planned on $date before ${habit.createdOn}")
                assertTrue(habit.archivedOn == null || date < habit.archivedOn, "seed $seed: ${habit.id} planned on $date after archive")
            }
        }
    }

    @Test
    fun habitsComeInDisplayOrderAndSessionsOfOneDayHaveUniqueIds() = forEachSeed { seed, _, snapshot, month ->
        val orderOf = snapshot.dailyHabits.associate { it.id to it.displayOrder }
        for (date in month.dates()) {
            val planned = snapshot.plannedHabitsOn(date)
            val orders = planned.map { orderOf.getValue(it.habit.id) }
            assertEquals(orders.sorted(), orders, "seed $seed, $date: display order")
            assertEquals(planned.size, planned.map { it.id }.toSet().size, "seed $seed, $date: ids must be unique per day")
        }
    }
}
