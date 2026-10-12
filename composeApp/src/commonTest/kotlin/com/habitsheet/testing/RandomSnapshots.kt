package com.habitsheet.testing

import com.habitsheet.domain.model.HabitKind
import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.domain.model.MonthKey
import kotlinx.datetime.DatePeriod
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import kotlin.random.Random

/** How many seeds a property test runs. Every failure message names its seed, so a failure is reproducible. */
const val PROPERTY_RUNS = 300

fun LocalDate.plusDays(days: Int): LocalDate = plus(DatePeriod(days = days))

/**
 * A random but internally consistent snapshot around [month]: habits created before, during or after it, some
 * archived, weekly and dated plans (several sessions on one day, skipped rest notes), sheet-managed habits and
 * check-offs inside and outside the month. Deterministic for a given [random].
 *
 * With [singleSessionPerDay] every plan row gets the id the single-session model uses (`habit|date`), which is what the
 * habit-keyed workbook functions in `HabitCalculations` can represent.
 */
fun randomSnapshot(random: Random, month: MonthKey, singleSessionPerDay: Boolean = false): HabitSnapshot = buildSnapshot(stamp = 1) {
    val first = month.firstDay
    val categoryIds = List(random.nextInt(0, 5)) { "c$it" }
    categoryIds.forEachIndexed { index, id -> category(id, "Category $index", order = categoryIds.size - index) }

    val habitIds = List(random.nextInt(1, 7)) { "h$it" }
    val usedPlanIds = mutableSetOf<String>() // a plan id is a primary key: never two rows with the same one
    val orders = habitIds.indices.shuffled(random)
    habitIds.forEachIndexed { index, id ->
        val created = first.plusDays(random.nextInt(-45, 20))
        val archived = if (random.nextInt(4) == 0) created.plusDays(random.nextInt(0, 60)) else null
        dailyHabit(
            id = id,
            name = "Habit $index",
            category = categoryIds.randomOrNull(random)?.takeIf { random.nextInt(5) != 0 },
            goal = random.nextInt(0, 32),
            order = orders[index],
            active = archived == null && random.nextInt(8) != 0,
            createdOn = created,
            archivedOn = archived,
            kind = if (random.nextInt(5) == 0) HabitKind.AVOIDANCE else HabitKind.ACTION,
            datedOnly = random.nextInt(3) == 0,
        )
        repeat(random.nextInt(0, 4)) { weeklyPlan(id, random.nextInt(1, 8), "weekly $index") }
        repeat(random.nextInt(0, 6)) { n ->
            val date = first.plusDays(random.nextInt(-3, month.daysInMonth + 3))
            val sessions = if (singleSessionPerDay) 1 else random.nextInt(1, 4)
            repeat(sessions) { session ->
                val planId = if (singleSessionPerDay || session == 0) "$id|$date" else "$id|$date|$session"
                if (usedPlanIds.add(planId)) dayPlan(id, date, "session $n.$session", skipped = random.nextInt(6) == 0, id = planId)
            }
        }
        if (random.nextInt(6) == 0) sheetManaged(id)
    }

    val planned = build().dayPlans.map { Triple(it.habitId, it.date, it.id) }
    repeat(random.nextInt(0, 60)) {
        val habit = habitIds.random(random)
        val date = first.plusDays(random.nextInt(-10, month.daysInMonth + 10))
        val planId = planned.filter { it.first == habit && it.second == date }.map { it.third }.randomOrNull(random) ?: "$habit|$date"
        done(habit, date, completed = random.nextInt(4) != 0, planId = planId)
    }

    repeat(random.nextInt(0, 4)) { index ->
        weeklyHabit(
            id = "w$index",
            category = categoryIds.randomOrNull(random),
            createdOn = first.plusDays(random.nextInt(-40, 25)),
            archivedOn = if (random.nextInt(5) == 0) first.plusDays(random.nextInt(0, 40)) else null,
            active = random.nextInt(6) != 0,
        )
        month.weekStarts().forEach { start -> if (random.nextBoolean()) weeklyDone("w$index", start, completed = random.nextInt(5) != 0) }
    }
}
