package com.habitsheet.domain.plan

import com.habitsheet.domain.plan.PlanCalendar.EventRule
import kotlinx.datetime.LocalDate

/**
 * Interview preparation: coding practice, system design (past the first level) and behavioural stories rotate through
 * the study days, a mock interview on the long day; the interview, when its date is known, is the last session.
 * Without a date the plan runs for eight weeks.
 */
internal object InterviewPlan {
    private const val MIN_DAYS = 7
    private const val DEFAULT_DAYS = 56
    private val codingTopics = listOf(
        PlanText.CODING_ARRAYS_STRINGS,
        PlanText.CODING_HASHING,
        PlanText.CODING_LINKED_LISTS,
        PlanText.CODING_STACKS_QUEUES,
        PlanText.CODING_TREES,
        PlanText.CODING_GRAPHS,
        PlanText.CODING_BINARY_SEARCH,
        PlanText.CODING_DYNAMIC_PROGRAMMING,
    )
    private val designTopics = listOf(
        PlanText.DESIGN_URL_SHORTENER,
        PlanText.DESIGN_RATE_LIMITER,
        PlanText.DESIGN_CHAT,
        PlanText.DESIGN_NEWS_FEED,
        PlanText.DESIGN_CACHE,
    )
    private val behaviouralTopics = listOf(
        PlanText.BEHAVIOURAL_INTRO,
        PlanText.BEHAVIOURAL_CONFLICT,
        PlanText.BEHAVIOURAL_FAILURE,
        PlanText.BEHAVIOURAL_LEADERSHIP,
        PlanText.BEHAVIOURAL_QUESTIONS_TO_ASK,
    )

    fun draft(questions: PlanQuestions, today: LocalDate): PlanResult {
        PlanCalendar.validate(questions, today, EventRule.OPTIONAL, MIN_DAYS)?.let { return PlanResult.Invalid(it) }
        val event = questions.eventDate
        val end = PlanCalendar.endDate(questions, today, EventRule.OPTIONAL, DEFAULT_DAYS)
        val days = PlanCalendar.trainingDays(questions.daysPerWeek, questions.restDay)
        val long = PlanCalendar.longDay(days)
        val withDesign = questions.level != PlanLevel.NEW
        val cycle = if (withDesign) listOf(CODING, DESIGN, CODING, BEHAVIOURAL) else listOf(CODING, CODING, BEHAVIOURAL)

        val lastStudyDay = if (event != null) end.minusOneDay() else end
        val sessions = mutableListOf<DraftSession>()
        var studied = 0
        val used = mutableMapOf(CODING to 0, DESIGN to 0, BEHAVIOURAL to 0)
        for ((_, date) in PlanCalendar.trainingDates(questions.startDate, lastStudyDay, days)) {
            if (date.dayOfWeek == long) {
                sessions += DraftSession(MOCK, date, PlanLabel(PlanText.MOCK_INTERVIEW_MIN, MOCK_MINUTES))
                continue
            }
            val habit = cycle[studied++ % cycle.size]
            val index = used.getValue(habit).also { used[habit] = it + 1 }
            val text = when (habit) {
                CODING -> codingTopics[index % codingTopics.size]
                DESIGN -> designTopics[index % designTopics.size]
                else -> behaviouralTopics[index % behaviouralTopics.size]
            }
            sessions += DraftSession(habit, date, PlanLabel(text))
        }
        val fitted = if (event == null) {
            sessions
        } else {
            val withInterview = sessions + DraftSession(MOCK, event, PlanLabel(PlanText.INTERVIEW_DAY))
            PlanCalendar.fitEventWeek(withInterview, event, questions.daysPerWeek, restBefore = false)
        }

        val studyDays = (days.size - 1).coerceAtLeast(1)
        val habits = listOfNotNull(
            sessionHabit(CODING, PlanText.HABIT_CODING, PlanCategory.CAREER, perWeek = studyDays / 2 + 1),
            sessionHabit(DESIGN, PlanText.HABIT_SYSTEM_DESIGN, PlanCategory.CAREER, perWeek = 1).takeIf { withDesign },
            sessionHabit(BEHAVIOURAL, PlanText.HABIT_BEHAVIOURAL, PlanCategory.CAREER, perWeek = 1),
            sessionHabit(MOCK, PlanText.HABIT_MOCK_INTERVIEW, PlanCategory.CAREER, perWeek = 1),
            everyDayHabit("review", PlanLabel(PlanText.HABIT_REVIEW_NOTES_MIN, REVIEW_MINUTES), PlanCategory.STUDY),
        )
        return PlanResult.Ok(PlanDraft(PlanTemplateId.INTERVIEW, habits, fitted))
    }

    private fun LocalDate.minusOneDay(): LocalDate = with(PlanCalendar) { plusDays(-1) }

    private const val CODING = "coding"
    private const val DESIGN = "design"
    private const val BEHAVIOURAL = "behavioural"
    private const val MOCK = "mock"
    private const val MOCK_MINUTES = 45
    private const val REVIEW_MINUTES = 15
}

/**
 * A healthy routine: full-body strength and cardio alternate, something active outdoors on the long day, with daily
 * steps, water and sleep. Twelve weeks; sessions get five minutes longer every three weeks, up to fifteen more.
 */
internal object GeneralFitnessPlan {
    private const val DEFAULT_DAYS = 84

    fun draft(questions: PlanQuestions, today: LocalDate): PlanResult {
        PlanCalendar.validate(questions, today, EventRule.IGNORED, minDaysToEvent = 0)?.let { return PlanResult.Invalid(it) }
        val end = PlanCalendar.endDate(questions, today, EventRule.IGNORED, DEFAULT_DAYS)
        val days = PlanCalendar.trainingDays(questions.daysPerWeek, questions.restDay)
        val long = PlanCalendar.longDay(days)
        val others = days.filter { it != long }
        val numbers = FitnessNumbers(questions.level.ordinal)

        val sessions = PlanCalendar.trainingDates(questions.startDate, end, days).map { (week, date) ->
            val minutes = numbers.minutes(week)
            val label = when {
                date.dayOfWeek == long -> PlanLabel(PlanText.ACTIVE_OUTDOORS_MIN, minutes + numbers.longDayExtraMinutes)
                others.indexOf(date.dayOfWeek) % 2 == 0 -> PlanLabel(PlanText.STRENGTH_FULL_BODY_MIN, minutes)
                else -> PlanLabel(PlanText.CARDIO_MIN, minutes)
            }
            DraftSession(WORKOUT, date, label)
        }
        val habits = listOf(
            sessionHabit(WORKOUT, PlanText.HABIT_WORKOUT, PlanCategory.FITNESS, perWeek = days.size),
            everyDayHabit("steps", PlanLabel(PlanText.HABIT_STEPS, numbers.steps), PlanCategory.HEALTH),
            everyDayHabit("water", PlanLabel(PlanText.HABIT_WATER_LITRES, numbers.waterLitres), PlanCategory.HEALTH),
            everyDayHabit("sleep", PlanLabel(PlanText.HABIT_SLEEP_HOURS, SLEEP_HOURS), PlanCategory.SLEEP),
        )
        return PlanResult.Ok(PlanDraft(PlanTemplateId.GENERAL_FITNESS, habits, sessions))
    }

    private const val WORKOUT = "workout"

    /** By level (0 new, 1 regular, 2 experienced): five minutes more every three weeks, at most fifteen. */
    private class FitnessNumbers(level: Int) {
        val longDayExtraMinutes = 15
        val steps = 6000 + 2000 * level
        val waterLitres = 2
        private val baseMinutes = 20 + 10 * level
        private val stepMinutes = 5
        private val stepEveryWeeks = 3
        private val maxSteps = 3

        fun minutes(week: Int): Int = baseMinutes + stepMinutes * minOf(week / stepEveryWeeks, maxSteps)
    }
}

/** The bundled templates. Synchronous inside; [PlanSource] is `suspend` for sources that are not. */
class TemplatePlanSource : PlanSource {
    override fun templates(): List<PlanTemplateId> = PlanTemplateId.entries

    override suspend fun draft(template: PlanTemplateId, questions: PlanQuestions, today: LocalDate): PlanResult = generate(template, questions, today)

    companion object {
        /** A short plan may not reach every rotation slot: a session habit without sessions is left out. */
        fun generate(template: PlanTemplateId, questions: PlanQuestions, today: LocalDate): PlanResult =
            when (val result = templateResult(template, questions, today)) {
                is PlanResult.Ok -> PlanResult.Ok(result.draft.withoutIdleSessionHabits())
                is PlanResult.Invalid -> result
            }

        private fun PlanDraft.withoutIdleSessionHabits(): PlanDraft {
            val used = sessions.mapTo(HashSet()) { it.habitKey }
            return copy(habits = habits.filter { !it.datedOnly || it.key in used })
        }

        private fun templateResult(template: PlanTemplateId, questions: PlanQuestions, today: LocalDate): PlanResult = when (template) {
            PlanTemplateId.MARATHON -> MarathonPlan.draft(questions, today)
            PlanTemplateId.HYROX -> HyroxPlan.draft(questions, today)
            PlanTemplateId.INTERVIEW -> InterviewPlan.draft(questions, today)
            PlanTemplateId.GENERAL_FITNESS -> GeneralFitnessPlan.draft(questions, today)
            PlanTemplateId.SCRATCH -> PlanResult.Ok(PlanDraft(PlanTemplateId.SCRATCH))
        }

        /** Which templates ask for an event date, and whether it is required. */
        fun eventDate(template: PlanTemplateId): EventDate = when (template) {
            PlanTemplateId.MARATHON, PlanTemplateId.HYROX -> EventDate.REQUIRED
            PlanTemplateId.INTERVIEW -> EventDate.OPTIONAL
            PlanTemplateId.GENERAL_FITNESS, PlanTemplateId.SCRATCH -> EventDate.NOT_ASKED
        }
    }

    enum class EventDate { REQUIRED, OPTIONAL, NOT_ASKED }
}
