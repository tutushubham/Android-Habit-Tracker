package com.habitsheet.domain.plan

import kotlinx.datetime.DatePeriod
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.minus
import kotlinx.datetime.plus

/** Calendar helpers shared by the templates. Pure and deterministic. */
internal object PlanCalendar {
    /** The order training days are chosen in: a long day at the weekend, quality days spread through the week. */
    private val preference = listOf(
        DayOfWeek.SATURDAY,
        DayOfWeek.TUESDAY,
        DayOfWeek.THURSDAY,
        DayOfWeek.SUNDAY,
        DayOfWeek.WEDNESDAY,
        DayOfWeek.MONDAY,
        DayOfWeek.FRIDAY,
    )

    /** [daysPerWeek] weekdays without [restDay], in week order. */
    fun trainingDays(daysPerWeek: Int, restDay: DayOfWeek?): List<DayOfWeek> =
        preference.filter { it != restDay }.take(daysPerWeek).sortedBy { it.isoDayNumber }

    /** The day for the week's long session: Saturday, else Sunday, else the last training day. */
    fun longDay(days: List<DayOfWeek>): DayOfWeek = when {
        DayOfWeek.SATURDAY in days -> DayOfWeek.SATURDAY
        DayOfWeek.SUNDAY in days -> DayOfWeek.SUNDAY
        else -> days.last()
    }

    fun mondayOf(date: LocalDate): LocalDate = date.minus(DatePeriod(days = date.dayOfWeek.isoDayNumber - 1))

    fun LocalDate.plusDays(days: Int): LocalDate = plus(DatePeriod(days = days))

    /** Number of Monday-to-Sunday weeks that touch the range [from]..[to] (both inclusive). */
    fun weeksBetween(from: LocalDate, to: LocalDate): Int = daysBetween(mondayOf(from), mondayOf(to)) / DAYS_PER_WEEK + 1

    /** Every date in [from]..[to] (inclusive) whose weekday is in [days], grouped by its week index from [from]'s week. */
    fun trainingDates(from: LocalDate, to: LocalDate, days: List<DayOfWeek>): List<Pair<Int, LocalDate>> {
        val firstMonday = mondayOf(from)
        val result = mutableListOf<Pair<Int, LocalDate>>()
        var date = from
        while (date <= to) {
            if (date.dayOfWeek in days) result += daysBetween(firstMonday, date) / DAYS_PER_WEEK to date
            date = date.plusDays(1)
        }
        return result
    }

    fun daysBetween(from: LocalDate, to: LocalDate): Int = (to.toEpochDays() - from.toEpochDays()).toInt()

    const val DAYS_PER_WEEK = 7

    /** The checks every template shares; [eventRule] says whether the template needs, accepts or ignores an event date. */
    fun validate(questions: PlanQuestions, today: LocalDate, eventRule: EventRule, minDaysToEvent: Int): PlanProblem? {
        val horizonEnd = today.plusDays(PLAN_HORIZON_DAYS)
        val event = questions.eventDate.takeIf { eventRule != EventRule.IGNORED }
        return when {
            questions.daysPerWeek !in PlanQuestions.MIN_DAYS_PER_WEEK..PlanQuestions.MAX_DAYS_PER_WEEK ->
                PlanProblem.DAYS_PER_WEEK_OUT_OF_RANGE

            questions.startDate < today -> PlanProblem.START_IN_THE_PAST

            questions.startDate > horizonEnd.plusDays(-minDaysToEvent) -> PlanProblem.EVENT_TOO_FAR

            eventRule == EventRule.REQUIRED && event == null -> PlanProblem.EVENT_DATE_MISSING

            event != null && daysBetween(questions.startDate, event) < minDaysToEvent -> PlanProblem.EVENT_TOO_SOON

            event != null && event > horizonEnd -> PlanProblem.EVENT_TOO_FAR

            else -> null
        }
    }

    /** The last plan day: the event, or [defaultDays] after the start, never beyond the horizon. */
    fun endDate(questions: PlanQuestions, today: LocalDate, eventRule: EventRule, defaultDays: Int): LocalDate {
        val event = questions.eventDate.takeIf { eventRule != EventRule.IGNORED }
        val wanted = event ?: questions.startDate.plusDays(defaultDays - 1)
        return minOf(wanted, today.plusDays(PLAN_HORIZON_DAYS))
    }

    /**
     * The event week keeps at most [daysPerWeek] sessions, the event included: the latest training sessions before it
     * are dropped. With [restBefore] the day before the event is free as well.
     */
    fun fitEventWeek(sessions: List<DraftSession>, event: LocalDate, daysPerWeek: Int, restBefore: Boolean): List<DraftSession> {
        val eventWeek = mondayOf(event)
        var result = if (restBefore) sessions.filterNot { it.date == event.plusDays(-1) } else sessions
        while (result.count { mondayOf(it.date) == eventWeek } > daysPerWeek) {
            val latest = result.filter { mondayOf(it.date) == eventWeek && it.date != event }.maxByOrNull { it.date } ?: break
            result = result - latest
        }
        return result
    }

    enum class EventRule { REQUIRED, OPTIONAL, IGNORED }
}

private const val WEEKS_PER_MONTH = 4
private const val MAX_MONTHLY_GOAL = 31

/** A monthly goal for a habit with [perWeek] sessions a week: about four weeks' worth, within 1..31. */
internal fun monthlyGoalFor(perWeek: Int): Int = (perWeek * WEEKS_PER_MONTH).coerceIn(1, MAX_MONTHLY_GOAL)
