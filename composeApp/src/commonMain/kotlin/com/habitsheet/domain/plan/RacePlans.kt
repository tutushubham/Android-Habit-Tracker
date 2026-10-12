package com.habitsheet.domain.plan

import com.habitsheet.domain.plan.PlanCalendar.EventRule
import com.habitsheet.domain.plan.PlanCalendar.plusDays
import kotlinx.datetime.LocalDate
import kotlin.math.roundToInt

/**
 * Marathon: a long run that builds to a peak three weeks out, cut back every fourth week and tapered over the last two;
 * easy runs on the other days, one interval session for runners past the first level, strength on a fifth day.
 * The race itself is the last session.
 */
internal object MarathonPlan {
    private const val MIN_DAYS = 28

    fun draft(questions: PlanQuestions, today: LocalDate): PlanResult {
        PlanCalendar.validate(questions, today, EventRule.REQUIRED, MIN_DAYS)?.let { return PlanResult.Invalid(it) }
        val event = checkNotNull(questions.eventDate)
        val days = PlanCalendar.trainingDays(questions.daysPerWeek, questions.restDay)
        val long = PlanCalendar.longDay(days)
        val others = days.filter { it != long }
        val intervalDay = others.firstOrNull()?.takeIf { questions.level != PlanLevel.NEW && questions.daysPerWeek >= INTERVALS_FROM_DAYS }
        val strengthDay = others.lastOrNull()?.takeIf { questions.daysPerWeek >= STRENGTH_FROM_DAYS && it != intervalDay }
        val numbers = Numbers.of(questions.level)

        val weeks = PlanCalendar.weeksBetween(questions.startDate, event)
        val raceWeek = weeks - 1
        val sessions = mutableListOf<DraftSession>()
        for ((week, date) in PlanCalendar.trainingDates(questions.startDate, event.plusDays(-1), days)) {
            val label = when {
                week == raceWeek -> PlanLabel(PlanText.RUN_EASY_KM, numbers.raceWeekEasyKm)
                date.dayOfWeek == long -> PlanLabel(PlanText.RUN_LONG_KM, numbers.longRunKm(week, weeks))
                date.dayOfWeek == intervalDay -> PlanLabel(PlanText.RUN_INTERVALS_800, numbers.intervals)
                date.dayOfWeek == strengthDay -> PlanLabel(PlanText.STRENGTH_CORE_MIN, STRENGTH_MINUTES)
                else -> PlanLabel(PlanText.RUN_EASY_KM, numbers.easyKm)
            }
            val habit = if (label.text == PlanText.STRENGTH_CORE_MIN) STRENGTH else RUNNING
            sessions += DraftSession(habit, date, label)
        }
        sessions += DraftSession(RUNNING, event, PlanLabel(PlanText.RACE_MARATHON))
        val fitted = PlanCalendar.fitEventWeek(sessions, event, questions.daysPerWeek, restBefore = true)

        val runDays = days.size - if (strengthDay != null) 1 else 0
        val habits = listOfNotNull(
            sessionHabit(RUNNING, PlanText.HABIT_RUNNING, PlanCategory.FITNESS, perWeek = runDays),
            strengthDay?.let { sessionHabit(STRENGTH, PlanText.HABIT_STRENGTH, PlanCategory.FITNESS, perWeek = 1) },
            everyDayHabit("stretch", PlanLabel(PlanText.HABIT_STRETCH_MIN, STRETCH_MINUTES), PlanCategory.HEALTH),
            everyDayHabit("sleep", PlanLabel(PlanText.HABIT_SLEEP_HOURS, SLEEP_HOURS), PlanCategory.SLEEP),
        )
        return PlanResult.Ok(PlanDraft(PlanTemplateId.MARATHON, habits, fitted))
    }

    private class Numbers(val easyKm: Int, val startLongKm: Int, val peakLongKm: Int, val intervals: Int) {
        val raceWeekEasyKm: Int = maxOf(3, easyKm / 2)

        /** Builds linearly to the peak three weeks before the race, 80 % every fourth week, then 75 % and 50 %. */
        fun longRunKm(week: Int, weeks: Int): Int {
            val buildWeeks = maxOf(1, weeks - TAPER_WEEKS - 1)
            val km = when {
                week >= weeks - TAPER_WEEKS -> peakLongKm * if (week == weeks - TAPER_WEEKS) FIRST_TAPER else SECOND_TAPER
                buildWeeks == 1 -> peakLongKm.toDouble()
                else -> startLongKm + (peakLongKm - startLongKm) * week.toDouble() / (buildWeeks - 1)
            }
            val cutback = week % CUTBACK_EVERY == CUTBACK_EVERY - 1 && week < weeks - TAPER_WEEKS
            return (if (cutback) km * CUTBACK else km).roundToInt().coerceAtMost(peakLongKm)
        }

        companion object {
            fun of(level: PlanLevel) = when (level) {
                PlanLevel.NEW -> Numbers(easyKm = 5, startLongKm = 8, peakLongKm = 30, intervals = 4)
                PlanLevel.REGULAR -> Numbers(easyKm = 8, startLongKm = 12, peakLongKm = 32, intervals = 6)
                PlanLevel.EXPERIENCED -> Numbers(easyKm = 10, startLongKm = 16, peakLongKm = 35, intervals = 8)
            }
        }
    }

    private const val TAPER_WEEKS = 3
    private const val CUTBACK_EVERY = 4
    private const val CUTBACK = 0.8
    private const val FIRST_TAPER = 0.75
    private const val SECOND_TAPER = 0.5
    private const val INTERVALS_FROM_DAYS = 3
    private const val STRENGTH_FROM_DAYS = 5
}

/**
 * Hyrox: running and station work alternate through the week (strength, intervals, stations, a compromised run that
 * mixes both), a long run on the long day; race week keeps one short run. The race is the last session.
 */
internal object HyroxPlan {
    private const val MIN_DAYS = 28
    private val rotation = listOf(PlanText.STRENGTH_FULL_BODY_MIN, PlanText.RUN_INTERVALS_1K, PlanText.HYROX_STATIONS, PlanText.HYROX_COMPROMISED_RUN)

    fun draft(questions: PlanQuestions, today: LocalDate): PlanResult {
        PlanCalendar.validate(questions, today, EventRule.REQUIRED, MIN_DAYS)?.let { return PlanResult.Invalid(it) }
        val event = checkNotNull(questions.eventDate)
        val days = PlanCalendar.trainingDays(questions.daysPerWeek, questions.restDay)
        val long = PlanCalendar.longDay(days)
        val others = days.filter { it != long }
        val numbers = HyroxNumbers(questions.level.ordinal)
        val raceWeek = PlanCalendar.weeksBetween(questions.startDate, event) - 1

        val sessions = mutableListOf<DraftSession>()
        for ((week, date) in PlanCalendar.trainingDates(questions.startDate, event.plusDays(-1), days)) {
            val label = when {
                week == raceWeek -> PlanLabel(PlanText.RUN_EASY_KM, numbers.raceWeekEasyKm)

                date.dayOfWeek == long -> PlanLabel(PlanText.RUN_LONG_KM, numbers.longRunKm(week))

                else -> when (val text = rotation[others.indexOf(date.dayOfWeek) % rotation.size]) {
                    PlanText.STRENGTH_FULL_BODY_MIN -> PlanLabel(text, numbers.strengthMinutes)
                    PlanText.RUN_INTERVALS_1K -> PlanLabel(text, numbers.intervals(week))
                    PlanText.HYROX_STATIONS -> PlanLabel(text, numbers.stationRounds)
                    else -> PlanLabel(text, numbers.compromisedRounds)
                }
            }
            val habit = if (label.text in RUNNING_TEXTS) RUNNING else HYROX
            sessions += DraftSession(habit, date, label)
        }
        sessions += DraftSession(HYROX, event, PlanLabel(PlanText.RACE_HYROX))
        val fitted = PlanCalendar.fitEventWeek(sessions, event, questions.daysPerWeek, restBefore = true)

        val runsPerWeek = 1 + others.count { rotation[others.indexOf(it) % rotation.size] == PlanText.RUN_INTERVALS_1K }
        val stationsPerWeek = days.size - runsPerWeek
        val habits = listOf(
            sessionHabit(HYROX, PlanText.HABIT_HYROX_TRAINING, PlanCategory.FITNESS, perWeek = stationsPerWeek),
            sessionHabit(RUNNING, PlanText.HABIT_RUNNING, PlanCategory.FITNESS, perWeek = runsPerWeek),
            everyDayHabit("mobility", PlanLabel(PlanText.HABIT_MOBILITY_MIN, STRETCH_MINUTES), PlanCategory.HEALTH),
            everyDayHabit("sleep", PlanLabel(PlanText.HABIT_SLEEP_HOURS, SLEEP_HOURS), PlanCategory.SLEEP),
        )
        return PlanResult.Ok(PlanDraft(PlanTemplateId.HYROX, habits, fitted))
    }

    /** Prescriptions by level (0 new, 1 regular, 2 experienced); long runs and intervals grow for a few weeks. */
    private class HyroxNumbers(level: Int) {
        val raceWeekEasyKm = 5
        val strengthMinutes = 40 + 5 * level
        val stationRounds = 4 + 2 * level
        val compromisedRounds = 4 + level
        private val longRunStartKm = 6 + 2 * level
        private val longRunGrowthWeeks = 4
        private val intervalsStart = 4 + level
        private val intervalsGrowthMax = 2
        private val intervalsGrowEvery = 2

        fun longRunKm(week: Int): Int = longRunStartKm + minOf(week, longRunGrowthWeeks)

        fun intervals(week: Int): Int = intervalsStart + minOf(week / intervalsGrowEvery, intervalsGrowthMax)
    }

    private val RUNNING_TEXTS = setOf(PlanText.RUN_EASY_KM, PlanText.RUN_LONG_KM, PlanText.RUN_INTERVALS_1K)
    private const val HYROX = "hyrox"
}

internal const val RUNNING = "running"
internal const val STRENGTH = "strength"
internal const val STRENGTH_MINUTES = 30
internal const val STRETCH_MINUTES = 10
internal const val SLEEP_HOURS = 8

/** Daily habits: most days of the month. */
internal const val DAILY_GOAL = 25

/** A habit that only appears on its session days; its goal is about four weeks of [perWeek] sessions. */
internal fun sessionHabit(key: String, name: PlanText, category: PlanCategory, perWeek: Int) =
    DraftHabit(key, PlanLabel(name), category, datedOnly = true, monthlyGoal = monthlyGoalFor(perWeek))

/** A habit due every day, aiming for most days of the month. */
internal fun everyDayHabit(key: String, name: PlanLabel, category: PlanCategory) =
    DraftHabit(key, name, category, datedOnly = false, monthlyGoal = DAILY_GOAL)
