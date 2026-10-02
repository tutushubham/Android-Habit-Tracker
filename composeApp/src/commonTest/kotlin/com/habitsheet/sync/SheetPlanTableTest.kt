package com.habitsheet.sync

import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.DayPlan
import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.domain.model.WeeklyPlan
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class SheetPlanTableTest {
    @Test
    fun acceptsIsoAndGoogleDateSerials() {
        val rows = parsePlanTable(Json.parseToJsonElement("""[
          ["ID","Date","Habit","Session","Done","Skip"],
          ["run-1","2026-10-01","Run","Easy 6 km",false,false],
          ["run-2",46327,"Run","Long 12 km",true,false]
        ]""").jsonArray)
        assertEquals(2, rows.size)
        assertEquals("2026-10-01", rows[0].date.toString())
        assertTrue(rows[1].done)
        assertEquals(3, rows[1].sheetRow)
    }

    @Test
    fun acceptsMultipleSessionsForSameHabitAndDay() {
        val table = Json.parseToJsonElement("""[
          ["ID","Date","Habit","Session","Done","Skip"],
          ["one","2026-10-01","Run","Easy",false,false],
          ["two","2026-10-01","run","Tempo",false,false]
        ]""").jsonArray
        val rows = parsePlanTable(table)
        assertEquals(2, rows.size)
        assertEquals(listOf("one", "two"), rows.map { it.id })
    }

    @Test
    fun acceptsEightColumnWorkbookAndRejectsDuplicateIds() {
        val table = Json.parseToJsonElement("""[
          ["ID","Date","Area","Habit","Session","Done","Skip","Source"],
          ["one","2026-10-01","Physical","Run","Easy",false,false,"Plan"],
          ["two","2026-10-01","Physical","Run","Mobility",false,false,"Plan"]
        ]""").jsonArray
        val rows = parsePlanTable(table)
        assertEquals("Physical", rows.first().area)
        assertEquals("F", rows.first().doneColumn)
        assertEquals(2, rows.size)
        val repeated = Json.parseToJsonElement("""[
          ["ID","Date","Habit","Session","Done","Skip"],
          ["one","2026-10-01","Run","Easy",false,false],
          ["one","2026-10-01","Run","Mobility",false,false]
        ]""").jsonArray
        assertFailsWith<IllegalArgumentException> { parsePlanTable(repeated) }
    }

    @Test
    fun rejectsWrongSchemaBeforeAnyWrite() {
        val table = Json.parseToJsonElement("""[["Date","Habit","Done"]]""").jsonArray
        assertFailsWith<IllegalArgumentException> { parsePlanTable(table) }
    }

    @Test
    fun rollingWindowRunsFromMonthStartMinus31ToTodayPlus180() {
        val window = SheetSyncWindow.rolling(LocalDate(2026, 10, 15))
        assertEquals(LocalDate(2026, 8, 31), window.start)
        assertEquals(LocalDate(2027, 4, 13), window.end)
    }

    @Test
    fun planTableIncludesDailyRoutinesAndDatedSessionsInWindow() {
        val start = LocalDate(2026, 10, 1)
        val window = SheetSyncWindow(start, LocalDate(2026, 12, 31))
        val run = DailyHabit("run", "Run", null, 12, 0, true, start, null, 1, 1, datedOnly = true)
        val protein = DailyHabit("protein", "Protein", null, 30, 1, true, start, null, 1, 1)
        val rows = planRowsFor(HabitSnapshot(
            dailyHabits = listOf(run, protein),
            dayPlans = listOf(
                DayPlan("run", start, "Easy 6 km", false, 1),
                DayPlan("run", LocalDate(2027, 2, 1), "Outside window", false, 1),
            ),
        ), window)
        assertEquals(93, rows.size)
        assertEquals(92, rows.count { it.habit == "Protein" })
        assertEquals(listOf("Easy 6 km"), rows.filter { it.habit == "Run" }.map { it.session })
    }
}
