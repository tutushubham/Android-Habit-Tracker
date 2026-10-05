package com.habitsheet.sync

import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.domain.model.plannedHabitsOn
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.minus
import kotlinx.datetime.plus
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive

/** Pure parsing and serialisation of the `Plan` tab. No IO. */
internal object PlanTable {
    val HEADER = listOf("ID", "Date", "Habit", "Session", "Done", "Skip")
}

/** Inclusive range of dates whose planned sessions are mirrored to the sheet. */
data class SheetSyncWindow(val start: LocalDate, val end: LocalDate) {
    companion object {
        const val DAYS_BEFORE_MONTH_START = 31
        const val DAYS_AFTER_TODAY = 180

        fun rolling(today: LocalDate): SheetSyncWindow = SheetSyncWindow(
            start = LocalDate(today.year, today.month, 1).minus(DAYS_BEFORE_MONTH_START, DateTimeUnit.DAY),
            end = today.plus(DAYS_AFTER_TODAY, DateTimeUnit.DAY),
        )
    }
}

/** Every planned session in [window], including bare daily and custom weekly habits. */
internal fun planRowsFor(snapshot: HabitSnapshot, window: SheetSyncWindow): List<SheetPlanRow> =
    (window.start.toEpochDays()..window.end.toEpochDays()).flatMap { dayNumber ->
        val date = LocalDate.fromEpochDays(dayNumber)
        snapshot.plannedHabitsOn(date).map { planned ->
            val detail = planned.detail ?: planned.habit.name
            val done = snapshot.dailyCompletions.any { it.planId == planned.id && it.completed }
            SheetPlanRow(planned.id, date, planned.habit.name, detail, done, planned.skipped, 0)
        }
    }

data class SheetPlanRow(val id: String, val date: LocalDate, val habit: String, val session: String, val done: Boolean, val skip: Boolean, val sheetRow: Int, val area: String? = null, val doneColumn: String = "E")

internal fun rowKey(row: SheetPlanRow) = row.id
internal fun rowKey(habit: String, date: LocalDate) = "${habit.trim().lowercase()}|$date"

internal fun parsePlanTable(values: JsonArray): List<SheetPlanRow> {
    val header = values.firstOrNull()?.jsonArray?.map { it.jsonPrimitive.content.trim() } ?: throw SyncError.MalformedPlanTab(null, "is empty")
    val eightColumns = header.take(8) == listOf("ID", "Date", "Area", "Habit", "Session", "Done", "Skip", "Source")
    if (!eightColumns && header.take(6) != listOf("ID", "Date", "Habit", "Session", "Done", "Skip")) {
        throw SyncError.MalformedPlanTab(null, "needs ID, Date, Habit, Session, Done, Skip (optional Area and Source columns)")
    }
    val ids = mutableSetOf<String>()
    return values.drop(1).mapIndexedNotNull { index, element ->
        val cells = element.jsonArray.map { it.jsonPrimitive.content.trim() }
        if (cells.all(String::isBlank)) return@mapIndexedNotNull null
        val id = cells.getOrElse(0) { "" }
        val dateCell = cells.getOrElse(1) { "" }
        val date = try {
            dateCell.toDoubleOrNull()?.let { serial ->
                LocalDate.fromEpochDays(LocalDate(1899, 12, 30).toEpochDays() + serial.toInt())
            } ?: LocalDate.parse(dateCell)
        } catch (_: Exception) {
            throw SyncError.MalformedPlanTab(index + 2, "use YYYY-MM-DD in Date")
        }
        val offset = if (eightColumns) 1 else 0
        val habit = cells.getOrElse(2 + offset) { "" }
        val session = cells.getOrElse(3 + offset) { "" }
        if (id.isBlank() || habit.isBlank() || session.isBlank()) throw SyncError.MalformedPlanTab(index + 2, "ID, Habit and Session are required")
        if (!ids.add(id)) throw SyncError.MalformedPlanTab(index + 2, "duplicate ID $id")
        val row = SheetPlanRow(
            id, date, habit, session,
            parseFlag(cells.getOrElse(4 + offset) { "" }, index + 2, "Done"),
            parseFlag(cells.getOrElse(5 + offset) { "" }, index + 2, "Skip"),
            index + 2,
            area = if (eightColumns) cells.getOrElse(2) { "" } else null,
            doneColumn = if (eightColumns) "F" else "E",
        )
        row
    }
}

private fun parseFlag(value: String, row: Int, column: String): Boolean = when (value.lowercase()) {
    "true", "yes", "1" -> true
    "false", "no", "0", "" -> false
    else -> throw SyncError.MalformedPlanTab(row, "$column must be a checkbox or TRUE/FALSE")
}

internal fun SheetPlanRow.asValues(): JsonArray = buildJsonArray {
    add(id)
    add(date.toString())
    add(habit)
    add(session)
    add(done)
    add(skip)
}
