package com.habitsheet.domain.plan

import com.habitsheet.testing.plusDays
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class PlanTemplatesTest {
    private val today = LocalDate(2026, 10, 12) // a Monday

    private fun draft(template: PlanTemplateId, questions: PlanQuestions): PlanDraft =
        assertIs<PlanResult.Ok>(TemplatePlanSource.generate(template, questions, today)).draft

    private fun problem(template: PlanTemplateId, questions: PlanQuestions): PlanProblem =
        assertIs<PlanResult.Invalid>(TemplatePlanSource.generate(template, questions, today)).problem

    @Test
    fun aSixteenWeekMarathonBuildsTheLongRunToItsPeakTapersAndRestsTheDayBeforeTheRace() {
        val race = today.plusDays(16 * 7 - 1) // Sunday of week 16
        val questions = PlanQuestions(today, race, PlanLevel.REGULAR, daysPerWeek = 4, restDay = DayOfWeek.MONDAY)
        val plan = draft(PlanTemplateId.MARATHON, questions)

        val longRuns = plan.sessions.filter { it.label.text == PlanText.RUN_LONG_KM }.map { it.label.args.single() }
        assertEquals(12, longRuns.first())
        assertEquals(32, longRuns.max())
        assertTrue(longRuns.takeLast(2).all { it < 32 }, "taper: $longRuns")
        assertEquals(PlanLabel(PlanText.RACE_MARATHON), plan.sessions.last().label)
        assertEquals(race, plan.sessions.last().date)
        assertTrue(plan.sessions.none { it.date == race.plusDays(-1) }, "rest the day before the race")
        assertTrue(plan.sessions.any { it.label.text == PlanText.RUN_INTERVALS_800 })
        assertEquals(listOf("running", "stretch", "sleep"), plan.habits.map { it.key })
        assertEquals(emptyList(), PlanInvariants.violations(plan, questions, today))
    }

    @Test
    fun newRunnersGetNoIntervalsAndFiveDaysAddAStrengthHabit() {
        val questions = PlanQuestions(today, today.plusDays(90), PlanLevel.NEW, daysPerWeek = 5)
        val plan = draft(PlanTemplateId.MARATHON, questions)
        assertTrue(plan.sessions.none { it.label.text == PlanText.RUN_INTERVALS_800 })
        assertTrue("strength" in plan.habits.map { it.key })
        assertTrue(plan.sessionsOf("strength").all { it.label == PlanLabel(PlanText.STRENGTH_CORE_MIN, 30) })
    }

    @Test
    fun raceTemplatesNeedAnEventDateThatIsNeitherTooSoonNorBeyondTheHorizon() {
        for (template in listOf(PlanTemplateId.MARATHON, PlanTemplateId.HYROX)) {
            assertEquals(PlanProblem.EVENT_DATE_MISSING, problem(template, PlanQuestions(today)))
            assertEquals(PlanProblem.EVENT_TOO_SOON, problem(template, PlanQuestions(today, today.plusDays(20))))
            assertEquals(PlanProblem.EVENT_TOO_FAR, problem(template, PlanQuestions(today, today.plusDays(PLAN_HORIZON_DAYS + 1))))
            assertIs<PlanResult.Ok>(TemplatePlanSource.generate(template, PlanQuestions(today, today.plusDays(PLAN_HORIZON_DAYS)), today))
        }
    }

    @Test
    fun answersOutsideTheAllowedRangeAreRejected() {
        val event = today.plusDays(60)
        assertEquals(PlanProblem.DAYS_PER_WEEK_OUT_OF_RANGE, problem(PlanTemplateId.MARATHON, PlanQuestions(today, event, daysPerWeek = 1)))
        assertEquals(PlanProblem.DAYS_PER_WEEK_OUT_OF_RANGE, problem(PlanTemplateId.GENERAL_FITNESS, PlanQuestions(today, daysPerWeek = 7)))
        assertEquals(PlanProblem.START_IN_THE_PAST, problem(PlanTemplateId.HYROX, PlanQuestions(today.plusDays(-1), event)))
    }

    @Test
    fun hyroxMixesRunningAndStationsAndEndsWithTheRace() {
        val questions = PlanQuestions(today, today.plusDays(70), PlanLevel.EXPERIENCED, daysPerWeek = 5, restDay = DayOfWeek.FRIDAY)
        val plan = draft(PlanTemplateId.HYROX, questions)
        val texts = plan.sessions.map { it.label.text }.toSet()
        assertTrue(texts.containsAll(listOf(PlanText.HYROX_STATIONS, PlanText.HYROX_COMPROMISED_RUN, PlanText.RUN_LONG_KM, PlanText.STRENGTH_FULL_BODY_MIN)), "$texts")
        assertTrue(plan.sessionsOf("running").all { it.label.text.name.startsWith("RUN_") })
        assertEquals(PlanLabel(PlanText.RACE_HYROX), plan.sessions.last().label)
        assertEquals(emptyList(), PlanInvariants.violations(plan, questions, today))
    }

    @Test
    fun interviewPrepWithoutADateRunsEightWeeksAndSkipsSystemDesignForBeginners() {
        val questions = PlanQuestions(today, level = PlanLevel.NEW, daysPerWeek = 3)
        val plan = draft(PlanTemplateId.INTERVIEW, questions)
        assertTrue(plan.sessions.maxOf { it.date } <= today.plusDays(55))
        assertTrue(plan.sessions.maxOf { it.date } > today.plusDays(48))
        assertTrue("design" !in plan.habits.map { it.key })
        assertTrue(plan.sessions.none { it.label.text == PlanText.INTERVIEW_DAY })
        assertTrue(plan.sessionsOf("mock").isNotEmpty())
    }

    @Test
    fun interviewPrepWithADateEndsOnTheInterviewAndRotatesTopics() {
        val interview = today.plusDays(30)
        val plan = draft(PlanTemplateId.INTERVIEW, PlanQuestions(today, interview, PlanLevel.EXPERIENCED, daysPerWeek = 5))
        assertEquals(PlanSessionCheck(interview, PlanText.INTERVIEW_DAY), plan.sessions.last().let { PlanSessionCheck(it.date, it.label.text) })
        val coding = plan.sessionsOf("coding").map { it.label.text }
        val firstRound = coding.take(8)
        assertEquals(firstRound.size, firstRound.toSet().size, "topics repeat only after all were used: $coding")
    }

    @Test
    fun generalFitnessIgnoresTheEventDateAndGrowsSessionsEveryThreeWeeks() {
        val questions = PlanQuestions(today, eventDate = today.plusDays(10), level = PlanLevel.REGULAR, daysPerWeek = 3)
        val plan = draft(PlanTemplateId.GENERAL_FITNESS, questions)
        assertTrue(plan.sessions.maxOf { it.date } > today.plusDays(70))
        val strength = plan.sessions.filter { it.label.text == PlanText.STRENGTH_FULL_BODY_MIN }.map { it.label.args.single() }
        assertEquals(30, strength.first())
        assertEquals(45, strength.last())
    }

    @Test
    fun startingFromScratchIsAnEmptyPlanAndEveryTemplateIsOffered() {
        assertEquals(PlanDraft(PlanTemplateId.SCRATCH), draft(PlanTemplateId.SCRATCH, PlanQuestions(today)))
        assertEquals(PlanTemplateId.entries, TemplatePlanSource().templates())
        assertEquals(TemplatePlanSource.EventDate.REQUIRED, TemplatePlanSource.eventDate(PlanTemplateId.MARATHON))
        assertEquals(TemplatePlanSource.EventDate.OPTIONAL, TemplatePlanSource.eventDate(PlanTemplateId.INTERVIEW))
        assertEquals(TemplatePlanSource.EventDate.NOT_ASKED, TemplatePlanSource.eventDate(PlanTemplateId.GENERAL_FITNESS))
    }

    @Test
    fun theInvariantsCatchBrokenPlans() {
        val questions = PlanQuestions(today, today.plusDays(40), daysPerWeek = 2, restDay = DayOfWeek.TUESDAY)
        val habit = DraftHabit("a", PlanLabel(PlanText.HABIT_RUNNING), PlanCategory.FITNESS, datedOnly = true, monthlyGoal = 40)
        val broken = PlanDraft(
            PlanTemplateId.MARATHON,
            listOf(habit, habit.copy(key = "b", monthlyGoal = 4)),
            listOf(
                DraftSession("a", today.plusDays(-1), PlanLabel(PlanText.RUN_EASY_KM, 5)),
                DraftSession("a", today.plusDays(1), PlanLabel(PlanText.RUN_EASY_KM)),
                DraftSession("a", today.plusDays(1), PlanLabel(PlanText.RUN_EASY_KM, 5)),
                DraftSession("a", today.plusDays(2), PlanLabel(PlanText.RUN_EASY_KM, 5)),
                DraftSession("a", today.plusDays(3), PlanLabel(PlanText.RUN_EASY_KM, 5)),
                DraftSession("x", today.plusDays(50), PlanLabel(PlanText.RUN_EASY_KM, 0)),
            ),
        )
        val violations = PlanInvariants.violations(broken, questions, today).joinToString("\n")
        for (expected in listOf("goal 40", "before the start", "wrong arguments", "two sessions", "unknown habit", "after the event", "rest day", "non-positive", "session habit b has no sessions", "more than 2")) {
            assertTrue(expected in violations, "missing '$expected' in\n$violations")
        }
    }

    private data class PlanSessionCheck(val date: LocalDate, val text: PlanText)
}
