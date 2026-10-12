package com.habitsheet.domain.plan

import com.habitsheet.domain.model.DayPlan
import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.domain.model.MAX_DAILY_HABITS
import com.habitsheet.testing.buildSnapshot
import com.habitsheet.testing.plusDays
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PlanMergerTest {
    private val today = LocalDate(2026, 10, 12)
    private val now = 1_000L

    private val words = mapOf(
        PlanText.HABIT_RUNNING to "Running",
        PlanText.HABIT_SLEEP_HOURS to "Sleep %d hours",
        PlanText.RUN_EASY_KM to "Easy run %d km",
        PlanText.CATEGORY_FITNESS to "Fitness",
        PlanText.CATEGORY_SLEEP to "Sleep",
    )

    private fun text(label: PlanLabel): String = label.args.fold(words.getValue(label.text)) { acc, arg -> acc.replaceFirst("%d", "$arg") }

    private val draft = PlanDraft(
        PlanTemplateId.MARATHON,
        listOf(
            DraftHabit("running", PlanLabel(PlanText.HABIT_RUNNING), PlanCategory.FITNESS, datedOnly = true, monthlyGoal = 12),
            DraftHabit("sleep", PlanLabel(PlanText.HABIT_SLEEP_HOURS, 8), PlanCategory.SLEEP, datedOnly = false, monthlyGoal = 25),
        ),
        listOf(
            DraftSession("running", today, PlanLabel(PlanText.RUN_EASY_KM, 5)),
            DraftSession("running", today.plusDays(2), PlanLabel(PlanText.RUN_EASY_KM, 6)),
        ),
    )

    private fun merge(snapshot: HabitSnapshot, plan: PlanDraft = draft): PlanMergeResult =
        PlanMerger.merge(snapshot, plan, wording, { "id-$it" }, PlanTime(today, now))

    private val wording = object : PlanWording {
        override fun text(label: PlanLabel) = this@PlanMergerTest.text(label)

        override fun copyName(name: String) = "$name (new plan)"
    }

    private fun ok(snapshot: HabitSnapshot, plan: PlanDraft = draft) = assertIs<PlanMergeResult.Ok>(merge(snapshot, plan))

    private val defaults = buildSnapshot {
        category("category-4", "Fitness 💪")
        category("category-8", "Sleep 💤")
    }

    @Test
    fun aFreshInstallGetsNewHabitsInTheDefaultCategoriesWithTheirSessions() {
        val result = ok(defaults)
        val app = result.application
        assertEquals(listOf("Running", "Sleep 8 hours"), app.habits.map { it.name })
        assertEquals(listOf("category-4", "category-8"), app.habits.map { it.categoryId })
        assertEquals(listOf(true, false), app.habits.map { it.datedOnly })
        assertEquals(listOf(0, 1), app.habits.map { it.displayOrder })
        assertTrue(app.habits.all { it.createdOn == today && it.updatedAtEpochMillis == now && it.active })
        assertEquals(emptyList(), app.categories)
        assertEquals(
            listOf(DayPlan("id-habit-running", today, "Easy run 5 km", updatedAtEpochMillis = now), DayPlan("id-habit-running", today.plusDays(2), "Easy run 6 km", updatedAtEpochMillis = now)),
            app.dayPlans,
        )
        assertEquals(0, result.reusedHabits)
    }

    @Test
    fun missingDefaultCategoriesAreFoundByNameOrCreatedOnce() {
        val snapshot = buildSnapshot { category("mine", "fitness") }
        val app = ok(snapshot).application
        assertEquals("mine", app.habits[0].categoryId)
        assertEquals(listOf("Sleep"), app.categories.map { it.name })
        assertEquals("id-category-sleep", app.habits[1].categoryId)
        assertEquals(1, app.categories.single().displayOrder)
    }

    @Test
    fun anExistingHabitWithTheSameNameIsReusedAndKeepsItsOwnPlanRows() {
        val snapshot = buildSnapshot {
            category("category-4")
            category("category-8")
            dailyHabit("mine", name = " running ", order = 3)
            dayPlan("mine", today, "My own run")
        }
        val result = ok(snapshot)
        assertEquals(1, result.reusedHabits)
        assertEquals(listOf("Sleep 8 hours"), result.application.habits.map { it.name })
        assertEquals(4, result.application.habits.single().displayOrder)
        assertEquals(listOf(today.plusDays(2)), result.application.dayPlans.map { it.date }, "today's row is the person's")
        assertTrue(result.application.dayPlans.all { it.habitId == "mine" })
    }

    @Test
    fun aSheetManagedHabitWithTheSameNameIsLeftAloneAndThePlanGetsItsOwnCopy() {
        val snapshot = buildSnapshot {
            dailyHabit("sheet", name = "Running")
            sheetManaged("sheet")
        }
        val app = ok(snapshot).application
        assertEquals("Running (new plan)", app.habits[0].name)
        assertTrue(app.dayPlans.all { it.habitId == "id-habit-running" })
    }

    @Test
    fun switchedOffHabitsAndTheirSessionsAreNotAdded() {
        val app = ok(defaults, draft.withHabitEnabled("running", false)).application
        assertEquals(listOf("Sleep 8 hours"), app.habits.map { it.name })
        assertEquals(emptyList(), app.dayPlans)
    }

    @Test
    fun applyingTheSamePlanAgainAddsNothing() {
        val app = ok(defaults).application
        val after = defaults.copy(dailyHabits = app.habits, dayPlans = app.dayPlans, categories = defaults.categories + app.categories)
        val again = ok(after)
        assertTrue(again.application.isEmpty, "${again.application}")
        assertEquals(2, again.reusedHabits)
    }

    @Test
    fun aPlanThatWouldPassTheHabitLimitIsRejectedBeforeAnythingIsWritten() {
        val full = buildSnapshot { repeat(MAX_DAILY_HABITS - 1) { dailyHabit("h$it", name = "Habit $it") } }
        assertEquals(PlanMergeResult.TooManyHabits(MAX_DAILY_HABITS, MAX_DAILY_HABITS + 1), merge(full))
        val archived = buildSnapshot { repeat(MAX_DAILY_HABITS) { dailyHabit("h$it", name = "Habit $it", active = false, archivedOn = today) } }
        assertIs<PlanMergeResult.Ok>(merge(archived), "archived habits do not count")
    }

    @Test
    fun everyLabelTheMergerAsksForIsListedUpFront() {
        val asked = mutableSetOf<PlanLabel>()
        val recording = object : PlanWording {
            override fun text(label: PlanLabel): String = this@PlanMergerTest.text(label).also { asked += label }

            override fun copyName(name: String) = name
        }
        val snapshot = buildSnapshot { }
        PlanMerger.merge(snapshot, draft, recording, { "id-$it" }, PlanTime(today, now))
        assertTrue(PlanMerger.labelsNeeded(draft).containsAll(asked), "asked $asked")
    }
}
