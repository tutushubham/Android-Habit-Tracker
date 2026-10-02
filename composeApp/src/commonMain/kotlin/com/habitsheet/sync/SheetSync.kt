package com.habitsheet.sync

import com.habitsheet.data.DefaultIdGenerator
import com.habitsheet.data.OndSeedData
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.DailyHabitCompletion
import com.habitsheet.domain.model.DayPlan
import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.domain.model.HabitKind
import com.habitsheet.domain.model.SheetLink
import com.habitsheet.domain.model.plannedHabitsOn
import com.habitsheet.domain.repository.HabitRepository
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.headers
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import io.ktor.http.ContentType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.CancellationException
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.*
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.time.Clock

/** Platform Google Sign-In owns tokens. The shared layer never persists credentials. */
interface SheetTokenProvider {
    fun requestToken(interactive: Boolean, completion: (String?, String?) -> Unit)
}

data class SheetSyncState(val busy: Boolean = false, val message: String = "Not synced yet", val lastSync: Long = 0)

/** A deliberately small, single-table Sheets client. Food/Workout/Marathon Plan are untouched. */
class SheetSync(
    private val repository: HabitRepository,
    private val tokenProvider: SheetTokenProvider,
    private val client: HttpClient = HttpClient(),
) {
    private val mutex = Mutex()
    private val mutableState = MutableStateFlow(SheetSyncState())
    val state: StateFlow<SheetSyncState> = mutableState.asStateFlow()

    suspend fun sync(interactive: Boolean = false) {
        mutex.lock()
        try {
            val id = SheetLink.spreadsheetId(repository.getSheetUrl())
            if (id == null) {
                if (interactive) mutableState.value = SheetSyncState(message = "Add a spreadsheet link first.")
                return
            }
            mutableState.value = mutableState.value.copy(busy = true, message = "Syncing…")
            val token = token(interactive)
            if (token == null) {
                mutableState.value = mutableState.value.copy(busy = false, message = if (interactive) "Google sign-in was cancelled." else "Sign in to sync")
                return
            }
            val api = SheetsApi(client, id, token)
            val snapshot = repository.snapshot.value
            val hasPlanTab = api.hasPlanTab()
            val createdTabId = if (!hasPlanTab) api.createPlanTab() else null
            val existingRows = if (hasPlanTab) api.readTable() else null
            if (existingRows == null) {
                val rows = ondRowsFor(snapshot)
                api.putTable(rows)
                if (createdTabId != null) {
                    try { api.formatPlanTab(createdTabId) }
                    catch (e: CancellationException) { throw e }
                    catch (_: Exception) { /* Formatting is optional; the data is already safe. */ }
                }
                repository.setSheetSyncedKeys(rows.map(::rowKey).toSet())
                val now = Clock.System.now().toEpochMilliseconds()
                repository.setSheetLastSync(now)
                removeSeedWeeklyPlaceholders(snapshot, rows.map { it.habit }.toSet())
                repository.setSheetManagedHabitIds(snapshot.sheetManagedHabitIds + rows.mapNotNull { row ->
                    snapshot.dailyHabits.firstOrNull { it.name.equals(row.habit, ignoreCase = true) }?.id
                })
                mutableState.value = SheetSyncState(message = "Plan tab created · ${rows.size} sessions uploaded", lastSync = now)
                return
            }
            var rows = existingRows
            val remoteHabitNames = rows.map { it.habit.trim().lowercase() }.toSet()
            val localOnlyIds = snapshot.dailyHabits
                .filter { it.id !in snapshot.sheetManagedHabitIds && it.name.trim().lowercase() !in remoteHabitNames }
                .map { it.id }.toSet()
            val additionalRows = ondRowsFor(snapshot).filter { row ->
                snapshot.dailyHabits.any { it.id in localOnlyIds && it.name.equals(row.habit, ignoreCase = true) }
            }
            if (additionalRows.isNotEmpty()) {
                api.appendRows(additionalRows)
                rows = api.readTable() ?: error("Could not read appended Plan rows.")
            }
            val oldKeys = repository.getSheetSyncedKeys()
            val lastSync = repository.getSheetLastSync()
            require(snapshot.dailyHabits.groupBy { it.name.trim().lowercase() }.values.all { it.size == 1 }) {
                "Two app habits have the same name. Rename one before syncing."
            }
            val localHabits = snapshot.dailyHabits.associateBy { it.name.trim().lowercase() }.toMutableMap()
            val pending = mutableListOf<Triple<Int, String, Boolean>>()
            val keys = rows.map(::rowKey).toSet()
            // A deleted sheet row deletes only a row previously imported from this sheet.
            val seedIds = OndSeedData.sessions.map { it.id }.toSet()
            val firstSyncSeedKeys = if (lastSync == 0L) snapshot.dayPlans.filter { plan ->
                plan.id in seedIds || (plan.id == "${plan.habitId}|${plan.date}" &&
                    snapshot.dailyHabits.firstOrNull { it.id == plan.habitId }?.name?.lowercase() in
                    setOf("run", "workout", "android", "dsa", "sde") && plan.date.year == 2026 &&
                    (plan.date.month.ordinal + 1) in 10..12)
            }.map { it.id }.toSet() else emptySet()
            for (key in (oldKeys + firstSyncSeedKeys) - keys) {
                val old = snapshot.dayPlans.firstOrNull { plan ->
                    plan.id == key || snapshot.dailyHabits.firstOrNull { it.id == plan.habitId }
                        ?.let { rowKey(it.name, plan.date) == key } == true
                } ?: continue
                repository.deleteDayPlanById(old.id)
            }
            for (row in rows) {
                val habitKey = row.habit.trim().lowercase()
                val habit = localHabits[habitKey] ?: run {
                    val created = DailyHabit(
                        id = DefaultIdGenerator().newId(),
                        name = row.habit,
                        categoryId = snapshot.categories.firstOrNull { category ->
                            category.name.substringBefore(' ').equals(categoryFor(row), ignoreCase = true)
                        }?.id,
                        monthlyGoal = 0,
                        displayOrder = localHabits.size,
                        active = true,
                        createdOn = row.date,
                        createdAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
                        updatedAtEpochMillis = Clock.System.now().toEpochMilliseconds(),
                        kind = if (row.habit.startsWith("No ", ignoreCase = true) || row.area == "Avoidance") HabitKind.AVOIDANCE else HabitKind.ACTION,
                        datedOnly = true,
                    )
                    repository.saveDailyHabit(created)
                    localHabits[habitKey] = created
                    created
                }
                val currentPlan = repository.snapshot.value.dayPlans.firstOrNull { it.id == row.id }
                if (currentPlan?.habitId != habit.id || currentPlan.date != row.date || currentPlan.detail != row.session || currentPlan.skipped != row.skip) {
                    repository.saveDayPlan(DayPlan(habit.id, row.date, row.session, row.skip, Clock.System.now().toEpochMilliseconds(), row.id))
                }
                val localDone = snapshot.dailyCompletions.firstOrNull { it.planId == row.id && it.date == row.date }
                if (lastSync > 0 && localDone != null && localDone.updatedAtEpochMillis > lastSync && localDone.completed != row.done) {
                    pending += Triple(row.sheetRow, row.doneColumn, localDone.completed)
                } else if (localDone?.completed != row.done) {
                    repository.setDailyCompletion(DailyHabitCompletion(habit.id, row.date, row.done, Clock.System.now().toEpochMilliseconds(), row.id))
                }
            }
            if (pending.isNotEmpty()) api.writeDone(pending)
            removeSeedWeeklyPlaceholders(
                repository.snapshot.value,
                rows.map { it.habit }.toSet() + if (lastSync == 0L) setOf("Run", "Workout", "Android", "DSA", "SDE") else emptySet(),
            )
            val managedNames = rows.map { it.habit.trim().lowercase() }.toSet() +
                if (lastSync == 0L) setOf("run", "workout", "android", "dsa", "sde") else emptySet()
            repository.setSheetManagedHabitIds(
                repository.snapshot.value.sheetManagedHabitIds + repository.snapshot.value.dailyHabits
                    .filter { it.name.trim().lowercase() in managedNames }.map { it.id },
            )
            val now = Clock.System.now().toEpochMilliseconds()
            repository.setSheetSyncedKeys(keys)
            repository.setSheetLastSync(now)
            mutableState.value = SheetSyncState(message = "Synced ${rows.size} sessions${if (pending.isNotEmpty()) " · ${pending.size} checks uploaded" else ""}", lastSync = now)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            mutableState.value = mutableState.value.copy(busy = false, message = e.message?.take(180) ?: "Sync failed. Try again.")
        } finally {
            mutex.unlock()
        }
    }

    private suspend fun token(interactive: Boolean): String? = suspendCancellableCoroutine { continuation ->
        tokenProvider.requestToken(interactive) { value, error ->
            if (continuation.isActive) {
                if (error != null) continuation.resumeWithException(IllegalStateException(error)) else continuation.resume(value)
            }
        }
    }

    fun close() = client.close()

    private suspend fun removeSeedWeeklyPlaceholders(snapshot: HabitSnapshot, names: Set<String>) {
        val normalized = names.map { it.trim().lowercase() }.toSet()
        val habitIds = snapshot.dailyHabits.filter { it.name.trim().lowercase() in normalized }.map { it.id }.toSet()
        snapshot.weeklyPlans.filter { it.habitId in habitIds && it.detail == "Plan in OND sheet" }.forEach {
            repository.deleteWeeklyPlan(it.habitId, it.weekday)
        }
    }
}

/** The personal starter plan covers OND 2026; bare daily and custom weekly habits get rows too. */
internal fun ondRowsFor(snapshot: HabitSnapshot): List<SheetPlanRow> {
    val start = LocalDate(2026, 10, 1).toEpochDays()
    val end = LocalDate(2026, 12, 31).toEpochDays()
    return (start..end).flatMap { dayNumber ->
        val date = LocalDate.fromEpochDays(dayNumber)
        snapshot.plannedHabitsOn(date).mapNotNull { planned ->
            if (planned.detail == "Plan in OND sheet") return@mapNotNull null
            val detail = planned.detail ?: planned.habit.name
            val done = snapshot.dailyCompletions.any { it.planId == planned.id && it.completed }
            SheetPlanRow(planned.id, date, planned.habit.name, detail, done, planned.skipped, 0)
        }
    }
}

data class SheetPlanRow(val id: String, val date: LocalDate, val habit: String, val session: String, val done: Boolean, val skip: Boolean, val sheetRow: Int, val area: String? = null, val doneColumn: String = "E")

private fun rowKey(row: SheetPlanRow) = row.id
private fun rowKey(habit: String, date: LocalDate) = "${habit.trim().lowercase()}|$date"

private fun categoryFor(row: SheetPlanRow): String = when (row.habit.lowercase()) {
    "no junk food" -> "Diet"
    "no adult content", "no gooning" -> "Health"
    "wake early", "sleep on time" -> "Sleep"
    "morning routine" -> "Productivity"
    else -> when (row.area?.lowercase()) {
        "physical" -> "Fitness"
        "mental" -> "Study"
        "social" -> "Social"
        else -> row.area.orEmpty()
    }
}

internal fun parsePlanTable(values: JsonArray): List<SheetPlanRow> {
    val header = values.firstOrNull()?.jsonArray?.map { it.jsonPrimitive.content.trim() } ?: error("Plan tab is empty.")
    val eightColumns = header.take(8) == listOf("ID", "Date", "Area", "Habit", "Session", "Done", "Skip", "Source")
    require(eightColumns || header.take(6) == listOf("ID", "Date", "Habit", "Session", "Done", "Skip")) {
        "Plan tab needs ID, Date, Habit, Session, Done, Skip (optional Area and Source columns)."
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
        } catch (_: Exception) { error("Plan row ${index + 2}: use YYYY-MM-DD in Date.") }
        val offset = if (eightColumns) 1 else 0
        val habit = cells.getOrElse(2 + offset) { "" }
        val session = cells.getOrElse(3 + offset) { "" }
        require(id.isNotBlank() && habit.isNotBlank() && session.isNotBlank()) { "Plan row ${index + 2}: ID, Habit, and Session are required." }
        require(ids.add(id)) { "Plan row ${index + 2}: duplicate ID $id." }
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
    else -> error("Plan row $row: $column must be a checkbox or TRUE/FALSE.")
}

private class SheetsApi(private val client: HttpClient, private val id: String, private val token: String) {
    private val base = "https://sheets.googleapis.com/v4/spreadsheets/$id"
    private suspend fun check(status: Int, body: String): String {
        if (status in 200..299) return body
        val explanation = when (status) {
            401 -> "Google session expired. Sign in again."
            403 -> "Sheet access denied. Check Sheets API, OAuth test user, and edit access."
            404 -> "Spreadsheet not found. Check the link and Google account."
            else -> "Google Sheets error $status."
        }
        error(explanation)
    }
    private fun io.ktor.client.request.HttpRequestBuilder.auth() {
        headers { append(HttpHeaders.Authorization, "Bearer $token") }
    }
    suspend fun hasPlanTab(): Boolean {
        val response = client.get(base) {
            auth()
            url { parameters.append("fields", "sheets(properties(title))") }
        }
        val data = Json.parseToJsonElement(check(response.status.value, response.bodyAsText())).jsonObject
        return data["sheets"]?.jsonArray?.any { it.jsonObject["properties"]?.jsonObject?.get("title")?.jsonPrimitive?.content == "Plan" } == true
    }
    suspend fun createPlanTab(): Int? {
        val response = client.post("$base:batchUpdate") {
            auth(); contentType(ContentType.Application.Json)
            setBody("""{"requests":[{"addSheet":{"properties":{"title":"Plan","gridProperties":{"frozenRowCount":1}}}}]}""")
        }
        val data = Json.parseToJsonElement(check(response.status.value, response.bodyAsText())).jsonObject
        return data["replies"]?.jsonArray?.firstOrNull()?.jsonObject?.get("addSheet")?.jsonObject
            ?.get("properties")?.jsonObject?.get("sheetId")?.jsonPrimitive?.intOrNull
    }
    suspend fun formatPlanTab(sheetId: Int) {
        val body = """{"requests":[
          {"setDataValidation":{"range":{"sheetId":$sheetId,"startRowIndex":1,"startColumnIndex":4,"endColumnIndex":6},"rule":{"condition":{"type":"BOOLEAN"},"strict":true,"showCustomUi":true}}},
          {"repeatCell":{"range":{"sheetId":$sheetId,"startRowIndex":0,"endRowIndex":1,"startColumnIndex":0,"endColumnIndex":6},"cell":{"userEnteredFormat":{"backgroundColor":{"red":0.08,"green":0.28,"blue":0.20},"textFormat":{"foregroundColor":{"red":1,"green":1,"blue":1},"bold":true}}},"fields":"userEnteredFormat"}},
          {"updateDimensionProperties":{"range":{"sheetId":$sheetId,"dimension":"COLUMNS","startIndex":3,"endIndex":4},"properties":{"pixelSize":420},"fields":"pixelSize"}}
        ]}"""
        val response = client.post("$base:batchUpdate") {
            auth(); contentType(ContentType.Application.Json); setBody(body)
        }
        check(response.status.value, response.bodyAsText())
    }
    suspend fun readTable(): List<SheetPlanRow>? {
        val response = client.get("$base/values/Plan!A:H") {
            auth()
            url { parameters.append("valueRenderOption", "UNFORMATTED_VALUE") }
        }
        val data = Json.parseToJsonElement(check(response.status.value, response.bodyAsText())).jsonObject
        val values = data["values"]?.jsonArray ?: return null
        return parsePlanTable(values)
    }
    suspend fun putTable(rows: List<SheetPlanRow>) {
        val values = buildJsonArray {
            add(buildJsonArray { listOf("ID", "Date", "Habit", "Session", "Done", "Skip").forEach { add(it) } })
            rows.forEach { add(it.asValues()) }
        }
        val response = client.put("$base/values/Plan!A1:F${rows.size + 1}") {
            auth(); contentType(ContentType.Application.Json)
            url { parameters.append("valueInputOption", "RAW") }
            setBody(buildJsonObject { put("values", values) }.toString())
        }
        check(response.status.value, response.bodyAsText())
    }
    suspend fun appendRows(rows: List<SheetPlanRow>) {
        val response = client.post("$base/values/Plan!A:F:append") {
            auth(); contentType(ContentType.Application.Json)
            url {
                parameters.append("valueInputOption", "RAW")
                parameters.append("insertDataOption", "INSERT_ROWS")
            }
            setBody(buildJsonObject {
                put("values", buildJsonArray { rows.forEach { add(it.asValues()) } })
            }.toString())
        }
        check(response.status.value, response.bodyAsText())
    }
    suspend fun writeDone(updates: List<Triple<Int, String, Boolean>>) {
        val body = buildJsonObject {
            put("valueInputOption", "RAW")
            put("data", buildJsonArray {
                updates.forEach { (row, column, done) -> add(buildJsonObject {
                    put("range", "Plan!$column$row")
                    put("values", buildJsonArray { add(buildJsonArray { add(done) }) })
                }) }
            })
        }
        val response = client.post("$base/values:batchUpdate") {
            auth(); contentType(ContentType.Application.Json); setBody(body.toString())
        }
        check(response.status.value, response.bodyAsText())
    }
}

private fun SheetPlanRow.asValues(): JsonArray = buildJsonArray {
    add(id); add(date.toString()); add(habit); add(session); add(done); add(skip)
}
