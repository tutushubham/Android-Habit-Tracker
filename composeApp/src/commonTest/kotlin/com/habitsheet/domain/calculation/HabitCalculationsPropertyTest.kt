package com.habitsheet.domain.calculation

import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.DailyHabitCompletion
import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.domain.model.MonthKey
import com.habitsheet.domain.model.plannedHabitsOn
import com.habitsheet.testing.PROPERTY_RUNS
import com.habitsheet.testing.plusDays
import com.habitsheet.testing.randomSnapshot
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Invariants of the workbook calculations (WORKBOOK_MAPPING.md) over random snapshots. The habit-keyed functions
 * cannot represent several sessions of one habit on a day, so those properties use single-session snapshots.
 */
class HabitCalculationsPropertyTest {
    private fun forEachSeed(singleSession: Boolean = true, body: (seed: Int, month: MonthKey, snapshot: HabitSnapshot, random: Random) -> Unit) {
        repeat(PROPERTY_RUNS) { seed ->
            val random = Random(10_000 + seed)
            val month = MonthKey(2023 + random.nextInt(4), random.nextInt(1, 13))
            body(seed, month, randomSnapshot(random, month, singleSessionPerDay = singleSession), random)
        }
    }

    @Test
    fun dailySummariesNeverCountMoreThanTheActiveHabitsAndAreCappedAtOneHundredPercent() = forEachSeed { seed, month, snapshot, _ ->
        val summaries = HabitCalculations.dailySummaries(month, snapshot.dailyHabits, snapshot.dailyCompletions)
        assertEquals(month.dates(), summaries.map { it.date }, "seed $seed")
        val doneKeys = snapshot.dailyCompletions.filter { it.completed }.map { it.habitId to it.date }.toSet()
        summaries.forEach { day ->
            val active = snapshot.dailyHabits.filter { it.isActiveOn(day.date) }
            assertEquals(active.size, day.activeHabits, "seed $seed ${day.date}")
            assertEquals(active.count { it.id to day.date in doneKeys }, day.completed, "seed $seed ${day.date}")
            assertEquals(day.activeHabits, day.completed + day.notCompleted, "seed $seed ${day.date}")
            assertTrue(day.completed in 0..day.activeHabits, "seed $seed ${day.date}")
            assertTrue(day.percentage in 0.0..1.0, "seed $seed ${day.date}")
            if (day.activeHabits == 0) assertEquals(0.0, day.percentage, "neutral when there are no habits")
        }
    }

    @Test
    fun habitSummariesCountOnlyCompletedCheckOffsInsideTheMonthForHabitsLiveThatMonth() = forEachSeed { seed, month, snapshot, _ ->
        val summaries = HabitCalculations.habitSummaries(month, snapshot.dailyHabits, snapshot.dailyCompletions)
        val dates = month.dates().toSet()
        val expectedHabits = snapshot.dailyHabits.filter { habit -> dates.any(habit::isActiveOn) }.map { it.id }.toSet()
        assertEquals(expectedHabits, summaries.map { it.habit.id }.toSet(), "seed $seed")
        assertEquals(summaries.map { it.habit.displayOrder }.sorted(), summaries.map { it.habit.displayOrder }, "seed $seed: display order")
        summaries.forEach { summary ->
            val expected = snapshot.dailyCompletions.count { it.habitId == summary.habit.id && it.completed && it.date in dates }
            assertEquals(expected, summary.completed, "seed $seed ${summary.habit.id}")
            assertEquals(summary.habit.monthlyGoal, summary.goal)
            assertTrue(summary.percentage >= 0.0)
            if (summary.goal == 0) assertEquals(0.0, summary.percentage, "a zero goal is neutral, never a division by zero")
        }
    }

    @Test
    fun aHabitMayExceedOneHundredPercentButACategoryNeverShowsNegativeRemaining() = forEachSeed { seed, month, snapshot, _ ->
        val habits = HabitCalculations.habitSummaries(month, snapshot.dailyHabits, snapshot.dailyCompletions)
        val categories = HabitCalculations.categorySummaries(snapshot.categories, habits)

        categories.forEach { summary ->
            val members = habits.filter { it.habit.categoryId == summary.category.id }
            assertTrue(members.isNotEmpty(), "seed $seed: a category with no habits is not listed")
            assertEquals(members.sumOf { it.goal }, summary.goal, "seed $seed")
            assertEquals(members.sumOf { it.completed }, summary.completed, "seed $seed")
            assertEquals(maxOf(summary.goal - summary.completed, 0), summary.remaining, "seed $seed")
            assertTrue(summary.remaining >= 0)
        }
        assertEquals(categories.map { it.category.displayOrder }.sorted(), categories.map { it.category.displayOrder }, "seed $seed")
    }

    @Test
    fun monthTotalIsTheSumOfHabitCompletionsOverTheSumOfHabitGoals() = forEachSeed { seed, month, snapshot, _ ->
        val habits = HabitCalculations.habitSummaries(month, snapshot.dailyHabits, snapshot.dailyCompletions)
        val progress = HabitCalculations.monthlyProgress(habits)
        assertEquals(habits.sumOf { it.completed }, progress.completed, "seed $seed")
        assertEquals(habits.sumOf { it.goal }, progress.goal, "seed $seed")
        assertEquals(progress.goal > 0, progress.hasGoal)
        if (progress.goal == 0) assertEquals(0.0, progress.percentage)
    }

    @Test
    fun weekBlocksPartitionTheDailyFiguresExactly() = forEachSeed { seed, month, snapshot, _ ->
        val days = HabitCalculations.dailySummaries(month, snapshot.dailyHabits, snapshot.dailyCompletions)
        val weeks = HabitCalculations.dailyWeekSummaries(month, days)

        assertEquals(if (month.daysInMonth == 28) 4 else 5, weeks.size, "seed $seed")
        assertEquals(month.dates(), weeks.flatMap { it.dates }, "seed $seed")
        assertEquals(days.sumOf { it.completed }, weeks.sumOf { it.completed }, "seed $seed")
        assertEquals(days.sumOf { it.activeHabits }, weeks.sumOf { it.goal }, "seed $seed")
        weeks.forEach { assertTrue(it.percentage in 0.0..1.0, "seed $seed week ${it.index}") }
        if (month.daysInMonth > 28) assertEquals(month.daysInMonth - 28, weeks.last().dates.size, "seed $seed: only the real days 29-31")
    }

    @Test
    fun weeklyBlocksNeverCompleteMoreThanTheirActiveHabits() = forEachSeed { seed, month, snapshot, _ ->
        val blocks = HabitCalculations.weeklyBlockSummaries(month, snapshot.weeklyHabits, snapshot.weeklyCompletions)
        assertEquals(month.weekStarts(), blocks.map { it.weekStartDate }, "seed $seed")
        blocks.forEach { block ->
            val dates = month.datesForWeek(block.index)
            assertEquals(snapshot.weeklyHabits.count { habit -> dates.any(habit::isActiveOn) }, block.goal, "seed $seed block ${block.index}")
            assertTrue(block.completed in 0..block.goal, "seed $seed block ${block.index}")
            assertEquals(block.goal - block.completed, block.notCompleted)
        }
        val overall = HabitCalculations.weeklyOverall(blocks)
        assertEquals(blocks.sumOf { it.completed }, overall.completed)
        assertEquals(blocks.sumOf { it.goal }, overall.goal)
    }

    @Test
    fun checkOffsOutsideTheMonthNeverChangeItsFigures() = forEachSeed { seed, month, snapshot, random ->
        val outside = snapshot.dailyHabits.flatMap { habit ->
            listOf(month.firstDay.plusDays(-1 - random.nextInt(0, 20)), month.lastDay.plusDays(1 + random.nextInt(0, 20)))
                .map { date -> DailyHabitCompletion(habit.id, date, true, 5) }
        }
        val before = HabitCalculations.habitSummaries(month, snapshot.dailyHabits, snapshot.dailyCompletions)
        val after = HabitCalculations.habitSummaries(month, snapshot.dailyHabits, snapshot.dailyCompletions + outside)
        assertEquals(before.map { it.habit.id to it.completed }, after.map { it.habit.id to it.completed }, "seed $seed")
        assertEquals(
            HabitCalculations.dailySummaries(month, snapshot.dailyHabits, snapshot.dailyCompletions).map { it.completed },
            HabitCalculations.dailySummaries(month, snapshot.dailyHabits, snapshot.dailyCompletions + outside).map { it.completed },
            "seed $seed",
        )
    }

    @Test
    fun archivingAHabitNeverAddsActiveDaysAndLeavesEarlierDaysUntouched() = forEachSeed { seed, month, snapshot, random ->
        // The app only archives habits that are active (archive = active false + archivedOn together).
        val habit = snapshot.dailyHabits.filter { it.active }.randomOrNull(random) ?: return@forEachSeed
        val archiveOn = month.firstDay.plusDays(random.nextInt(0, month.daysInMonth))
        val archived: List<DailyHabit> = snapshot.dailyHabits.map {
            if (it.id == habit.id) it.copy(active = false, archivedOn = archiveOn) else it
        }
        val before = HabitCalculations.dailySummaries(month, snapshot.dailyHabits, snapshot.dailyCompletions)
        val after = HabitCalculations.dailySummaries(month, archived, snapshot.dailyCompletions)
        before.zip(after).forEach { (b, a) ->
            assertTrue(a.activeHabits <= b.activeHabits, "seed $seed ${a.date}")
            if (a.date < archiveOn) assertEquals(b, a, "seed $seed: days before the archive day are history")
        }
    }

    @Test
    fun plannedShareSummaryCountsOnlyDueNonSkippedSessionsAndNeverExceedsTheTotal() = forEachSeed(singleSession = false) { seed, month, snapshot, _ ->
        for (date in month.dates()) {
            val summary = HabitCalculations.plannedDailyShareSummary(date, snapshot)
            val due = snapshot.plannedHabitsOn(date).filterNot { it.skipped }
            assertEquals(due.size, summary.totalCount, "seed $seed $date")
            assertEquals(due.map { it.id }.toSet(), (summary.doneHabits + summary.leftHabits).map { it.id }.toSet(), "seed $seed $date")
            assertTrue(summary.completedCount in 0..summary.totalCount, "seed $seed $date")
            assertTrue(summary.percentage in 0.0..1.0, "seed $seed $date")
            if (summary.totalCount == 0) assertEquals(0.0, summary.percentage)
        }
    }
}
