package com.habitsheet.sync

import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandleScope
import io.ktor.client.engine.mock.respond
import io.ktor.client.engine.mock.toByteArray
import io.ktor.client.plugins.HttpRequestTimeoutException
import io.ktor.client.request.HttpRequestData
import io.ktor.client.request.HttpResponseData
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.http.HttpStatusCode
import io.ktor.http.headersOf
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put

/** One request seen by [FakeSheetsServer]. [path] is everything after `/v4/spreadsheets/{id}`. */
data class RecordedCall(val method: String, val path: String, val query: String, val body: String) {
    val isAddSheet get() = path == ":batchUpdate" && "addSheet" in body
    val isFormat get() = path == ":batchUpdate" && "addSheet" !in body
    val isMetadata get() = method == "GET" && path.isEmpty()
    val isRead get() = method == "GET" && path.startsWith("/values/Plan!A:H")
    val isPut get() = method == "PUT" && path.startsWith("/values/Plan!")
    val isAppend get() = method == "POST" && path.endsWith(":append")
    val isDoneWrite get() = method == "POST" && path == "/values:batchUpdate"
    val isWrite get() = method != "GET"
    override fun toString() = "$method $path"
}

/** What an injected fault does when it fires. */
sealed interface FaultAction {
    data class Status(val code: Int, val headers: Map<String, String> = emptyMap()) : FaultAction
    data object Timeout : FaultAction
}

/**
 * In-memory stand-in for the slice of the Sheets v4 API that [SheetSync] uses: spreadsheet metadata,
 * `Plan` values (A:H), `addSheet`/format `batchUpdate`, `values:batchUpdate`, `PUT` and `append`.
 * [otherTabs] exist only so tests can prove they are never touched.
 */
class FakeSheetsServer(val spreadsheetId: String = "test-sheet") {
    val otherTabs = mutableListOf<String>()
    var hasPlanTab = false
        private set
    /** Row 0 is the header, exactly as stored in the sheet. */
    val planValues = mutableListOf<MutableList<JsonElement>>()
    val calls = mutableListOf<RecordedCall>()
    private val faults = mutableListOf<Fault>()
    private var nextSheetId = 100

    private class Fault(val matches: (RecordedCall) -> Boolean, var skip: Int, var remaining: Int, val action: FaultAction)

    /** Pre-populates an existing Plan tab. */
    fun withPlanTab(header: List<Any>, vararg rows: List<Any>): FakeSheetsServer {
        hasPlanTab = true
        planValues.clear()
        planValues += header.map(::toCell).toMutableList()
        rows.forEach { row -> planValues += row.map(::toCell).toMutableList() }
        return this
    }

    fun withPlanRows(vararg rows: List<Any>) = withPlanTab(SIX_COLUMNS, *rows)

    /** Fails [times] calls matching [matches], after letting [afterMatching] matching calls through. */
    fun fail(action: FaultAction, afterMatching: Int = 0, times: Int = 1, matches: (RecordedCall) -> Boolean = { true }) {
        faults += Fault(matches, afterMatching, times, action)
    }

    /** Fails the call that would be number [n] (1-based) of all calls. */
    fun failCallNumber(n: Int, action: FaultAction) = fail(action, afterMatching = n - 1)

    fun cell(row: Int, column: Int): JsonElement? = planValues.getOrNull(row)?.getOrNull(column)
    fun rowsAsText(): List<List<String>> = planValues.map { row -> row.map { it.jsonPrimitive.content } }
    val writes get() = calls.filter { it.isWrite }

    fun client(): HttpClient = HttpClient(MockEngine { request -> handle(request) })

    private fun MockRequestHandleScope.json(element: JsonObject, status: HttpStatusCode = HttpStatusCode.OK) =
        respond(element.toString(), status, headersOf(HttpHeaders.ContentType, "application/json"))

    private suspend fun MockRequestHandleScope.handle(request: HttpRequestData): HttpResponseData {
        val url = request.url
        val path = url.encodedPath.removePrefix("/v4/spreadsheets/$spreadsheetId").replace("%21", "!").replace("%3A", ":")
        val body = if (request.method == HttpMethod.Get) "" else request.body.toByteArray().decodeToString()
        val call = RecordedCall(request.method.value, path, url.encodedQuery, body)
        calls += call

        val fault = faults.firstOrNull { it.remaining > 0 && it.matches(call) }
        if (fault != null) {
            if (fault.skip > 0) {
                fault.skip--
            } else {
                fault.remaining--
                when (val action = fault.action) {
                    FaultAction.Timeout -> throw HttpRequestTimeoutException(url.toString(), 1)
                    is FaultAction.Status -> return respond(
                        """{"error":{"code":${action.code}}}""",
                        HttpStatusCode.fromValue(action.code),
                        Headers.build {
                            append(HttpHeaders.ContentType, "application/json")
                            action.headers.forEach { (k, v) -> append(k, v) }
                        },
                    )
                }
            }
        }
        return when {
            call.isMetadata -> json(buildJsonObject {
                put("sheets", buildJsonArray {
                    (otherTabs + listOfNotNull("Plan".takeIf { hasPlanTab })).forEach { title ->
                        add(buildJsonObject { put("properties", buildJsonObject { put("title", title) }) })
                    }
                })
            })
            call.isAddSheet -> {
                if (hasPlanTab) return json(buildJsonObject { put("error", "duplicate") }, HttpStatusCode.BadRequest)
                hasPlanTab = true
                planValues.clear()
                val id = nextSheetId++
                json(buildJsonObject {
                    put("replies", buildJsonArray {
                        add(buildJsonObject {
                            put("addSheet", buildJsonObject { put("properties", buildJsonObject { put("sheetId", id) }) })
                        })
                    })
                })
            }
            call.isFormat -> json(buildJsonObject {})
            call.isRead -> {
                if (!hasPlanTab) return json(buildJsonObject { put("error", "no Plan tab") }, HttpStatusCode.BadRequest)
                json(buildJsonObject {
                    if (planValues.isNotEmpty()) put("values", JsonArray(planValues.map { JsonArray(it) }))
                })
            }
            call.isPut -> {
                planValues.clear()
                valuesOf(body).forEach { planValues += it.toMutableList() }
                json(buildJsonObject {})
            }
            call.isAppend -> {
                valuesOf(body).forEach { planValues += it.toMutableList() }
                json(buildJsonObject {})
            }
            call.isDoneWrite -> {
                Json.parseToJsonElement(body).jsonObject["data"]!!.jsonArray.forEach { update ->
                    val range = update.jsonObject["range"]!!.jsonPrimitive.content // e.g. Plan!E5
                    val match = Regex("""Plan!([A-Z])(\d+)""").matchEntire(range)!!
                    val column = match.groupValues[1][0] - 'A'
                    val row = match.groupValues[2].toInt() - 1
                    val value = update.jsonObject["values"]!!.jsonArray[0].jsonArray[0]
                    val target = planValues[row]
                    while (target.size <= column) target += JsonPrimitive("")
                    target[column] = value
                }
                json(buildJsonObject {})
            }
            else -> json(buildJsonObject { put("error", "unmodelled $call") }, HttpStatusCode.NotFound)
        }
    }

    private fun valuesOf(body: String): List<List<JsonElement>> =
        Json.parseToJsonElement(body).jsonObject["values"]!!.jsonArray.map { it.jsonArray.toList() }

    companion object {
        val SIX_COLUMNS = listOf("ID", "Date", "Habit", "Session", "Done", "Skip")
        val EIGHT_COLUMNS = listOf("ID", "Date", "Area", "Habit", "Session", "Done", "Skip", "Source")
        private fun toCell(value: Any): JsonElement = when (value) {
            is Boolean -> JsonPrimitive(value)
            is Number -> JsonPrimitive(value)
            else -> JsonPrimitive(value.toString())
        }
    }
}
