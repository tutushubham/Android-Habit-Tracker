package com.habitsheet.domain.plan

import com.habitsheet.domain.model.MAX_DAILY_HABITS
import com.habitsheet.domain.plan.PlanCalendar.plusDays
import kotlinx.datetime.LocalDate

/**
 * The rules every plan must keep, whatever its source (bundled template today, a generated plan later). Returns the
 * broken rules, empty when the draft is fine. The event (the last session of a race or interview plan) may fall on the
 * rest day; nothing else may.
 */
object PlanInvariants {
    fun violations(draft: PlanDraft, questions: PlanQuestions, today: LocalDate): List<String> =
        habitViolations(draft) + sessionViolations(draft, questions, today) + shapeViolations(draft, questions)

    private fun habitViolations(draft: PlanDraft): List<String> = buildList {
        val keys = draft.habits.map { it.key }
        if (keys.size != keys.toSet().size) add("duplicate habit keys")
        if (draft.habits.size > MAX_DAILY_HABITS) add("more than $MAX_DAILY_HABITS habits")
        draft.habits.filter { it.monthlyGoal !in 1..MAX_GOAL }.forEach { add("goal ${it.monthlyGoal} of ${it.key} outside 1..$MAX_GOAL") }
        draft.habits.filter { it.label.args.size != it.label.text.argCount }.forEach { add("habit ${it.key} has wrong arguments") }
        draft.habits.filter { it.datedOnly && draft.sessions.none { s -> s.habitKey == it.key } }
            .forEach { add("session habit ${it.key} has no sessions") }
    }

    private fun sessionViolations(draft: PlanDraft, questions: PlanQuestions, today: LocalDate): List<String> = buildList {
        val keys = draft.habits.mapTo(HashSet()) { it.key }
        val horizon = today.plusDays(PLAN_HORIZON_DAYS)
        val event = questions.eventDate.takeIf { draft.template != PlanTemplateId.GENERAL_FITNESS }
        for (session in draft.sessions) {
            val at = "session ${session.date}"
            if (session.habitKey !in keys) add("session for unknown habit ${session.habitKey}")
            if (session.date < questions.startDate) add("$at before the start")
            if (session.date > horizon) add("$at beyond the horizon")
            if (event != null && session.date > event) add("$at after the event")
            if (session.date.dayOfWeek == questions.restDay && session.date != event) add("$at on the rest day")
            if (session.label.args.size != session.label.text.argCount) add("$at has wrong arguments")
            if (session.label.args.any { it <= 0 }) add("$at has a non-positive amount")
        }
    }

    private fun shapeViolations(draft: PlanDraft, questions: PlanQuestions): List<String> = buildList {
        draft.sessions.groupBy { it.habitKey to it.date }.filterValues { it.size > 1 }.keys
            .forEach { add("two sessions of ${it.first} on ${it.second}") }
        draft.sessions.groupBy { PlanCalendar.mondayOf(it.date) }
            .filterValues { week -> week.map { it.date }.toSet().size > questions.daysPerWeek }
            .keys.forEach { add("more than ${questions.daysPerWeek} session days in the week of $it") }
    }

    private const val MAX_GOAL = 31
}
