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

/** One request seen by [FakeSheetsServer]. [path] is everything after `/v4/spreadsheets/{id}` (a create keeps its whole path). */
data class RecordedCall(val method: String, val path: String, val query: String, val body: String) {
    val isAddSheet get() = path == ":batchUpdate" && "addSheet" in body
    val isFormat get() = path == ":batchUpdate" && "addSheet" !in body
    val isMetadata get() = method == "GET" && path.isEmpty()
    val isCreate get() = method == "POST" && path == "/v4/spreadsheets"
    val isRead get() = method == "GET" && path.startsWith("/values/Plan!A:H")
    val isPut get() = method == "PUT" && path.startsWith("/values/Plan!")
    val isAppend get() = method == "POST" && path.endsWith(":append")
    val isDoneWrite get() = method == "POST" && path == "/values:batchUpdate"
    val isWrite get() = method != "GET"
    override fun toString() = "$method $path"
}

/** What an injected fault does when it fires. */
sealed interface FaultAction {
    data class Status(val code: Int, val headers: Map<String, String> = emptyMap(), val body: String? = null) : FaultAction
    data object Timeout : FaultAction

    /** No route / DNS failure: an IOException before any response. */
    data object ConnectionFailure : FaultAction

    /**
     * The request reaches the server and is applied, but the reply never arrives (the connection drops, the app is
     * killed mid-request). The client sees a timeout while the sheet has already changed.
     */
    data object LostResponse : FaultAction
}

/**
 * In-memory stand-in for the slice of the Sheets v4 API that [SheetSync] uses: spreadsheet metadata,
 * `Plan` values (A:H), `addSheet`/format `batchUpdate`, `values:batchUpdate`, `PUT` and `append`, and creating a
 * spreadsheet (which this fake then becomes: one empty `Plan` tab, counted in [spreadsheetsCreated]).
 * [otherTabs] exist only so tests can prove they are never touched.
 */
class FakeSheetsServer(val spreadsheetId: String = "test-sheet") {
    val otherTabs = mutableListOf<String>()
    var hasPlanTab = false
        private set

    /** Row 0 is the header, exactly as stored in the sheet. */
    val planValues = mutableListOf<MutableList<JsonElement>>()
    val calls = mutableListOf<RecordedCall>()

    /** How many spreadsheets were created (POST /v4/spreadsheets) and their titles. */
    var spreadsheetsCreated = 0
        private set
    val createdTitles = mutableListOf<String>()

    /** Writes that targeted anything but the Plan tab. The fake rejects them (400); tests assert this stays empty. */
    val violations = mutableListOf<String>()
    private var planSheetId = -1
    private val faults = mutableListOf<Fault>()
    private var nextSheetId = 100

    private class Fault(val matches: (RecordedCall) -> Boolean, var skip: Int, var remaining: Int, val action: FaultAction)

    /** Pre-populates an existing Plan tab. */
    fun withPlanTab(header: List<Any>, vararg rows: List<Any>): FakeSheetsServer {
        hasPlanTab = true
        planSheetId = nextSheetId++
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

    fun clearFaults() = faults.clear()

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

        var lostResponse = false
        val fault = faults.firstOrNull { it.remaining > 0 && it.matches(call) }
        if (fault != null) {
            if (fault.skip > 0) {
                fault.skip--
            } else {
                fault.remaining--
                when (val action = fault.action) {
                    FaultAction.Timeout -> timedOut(url.toString())

                    FaultAction.ConnectionFailure -> throw kotlinx.io.IOException("Unable to resolve host")

                    FaultAction.LostResponse -> lostResponse = true

                    is FaultAction.Status -> return respond(
                        action.body ?: """{"error":{"code":${action.code}}}""",
                        HttpStatusCode.fromValue(action.code),
                        Headers.build {
                            append(HttpHeaders.ContentType, "application/json")
                            action.headers.forEach { (k, v) -> append(k, v) }
                        },
                    )
                }
            }
        }
        writeViolation(call)?.let { reason ->
            violations += reason
            return json(buildJsonObject { put("error", reason) }, HttpStatusCode.BadRequest)
        }
        val response = when {
            call.isCreate -> {
                spreadsheetsCreated++
                createdTitles += Json.parseToJsonElement(body).jsonObject["properties"]!!.jsonObject["title"]!!.jsonPrimitive.content
                otherTabs.clear()
                hasPlanTab = true
                planValues.clear()
                planSheetId = nextSheetId++
                json(
                    buildJsonObject {
                        put("spreadsheetId", spreadsheetId)
                        put("spreadsheetUrl", "https://docs.google.com/spreadsheets/d/$spreadsheetId/edit#gid=$planSheetId")
                        put(
                            "sheets",
                            buildJsonArray {
                                add(
                                    buildJsonObject {
                                        put(
                                            "properties",
                                            buildJsonObject {
                                                put("sheetId", planSheetId)
                                                put("title", "Plan")
                                            },
                                        )
                                    },
                                )
                            },
                        )
                    },
                )
            }

            call.isMetadata -> json(
                buildJsonObject {
                    put(
                        "sheets",
                        buildJsonArray {
                            otherTabs.forEachIndexed { index, title ->
                                add(
                                    buildJsonObject {
                                        put(
                                            "properties",
                                            buildJsonObject {
                                                put("title", title)
                                                put("sheetId", index + 1)
                                            },
                                        )
                                    },
                                )
                            }
                            if (hasPlanTab) {
                                add(
                                    buildJsonObject {
                                        put(
                                            "properties",
                                            buildJsonObject {
                                                put("title", "Plan")
                                                put("sheetId", planSheetId)
                                            },
                                        )
                                    },
                                )
                            }
                        },
                    )
                },
            )

            call.isAddSheet -> {
                // Google rejects duplicate tab names case-insensitively.
                if (hasPlanTab || otherTabs.any { it.equals("Plan", ignoreCase = true) }) {
                    return json(buildJsonObject { put("error", "duplicate") }, HttpStatusCode.BadRequest)
                }
                hasPlanTab = true
                planValues.clear()
                val id = nextSheetId++
                planSheetId = id
                json(
                    buildJsonObject {
                        put(
                            "replies",
                            buildJsonArray {
                                add(
                                    buildJsonObject {
                                        put("addSheet", buildJsonObject { put("properties", buildJsonObject { put("sheetId", id) }) })
                                    },
                                )
                            },
                        )
                    },
                )
            }

            call.isFormat -> json(buildJsonObject {})

            call.isRead -> {
                if (!hasPlanTab) return json(buildJsonObject { put("error", "no Plan tab") }, HttpStatusCode.BadRequest)
                json(
                    buildJsonObject {
                        if (planValues.isNotEmpty()) put("values", JsonArray(planValues.map { JsonArray(it) }))
                    },
                )
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
        if (lostResponse) timedOut(url.toString())
        return response
    }

    private fun timedOut(url: String): Nothing = throw HttpRequestTimeoutException(url, 1)

    /** Null if the write only touches the Plan tab; otherwise why it does not. */
    private fun writeViolation(call: RecordedCall): String? = when {
        !call.isWrite -> null

        call.isCreate -> createViolation(call)

        call.isAddSheet -> if (Regex(""""title"\s*:\s*"Plan"""").containsMatchIn(call.body)) null else "addSheet with a title other than Plan"

        call.isFormat -> {
            val ids = Regex(""""sheetId"\s*:\s*(\d+)""").findAll(call.body).map { it.groupValues[1].toInt() }.toSet()
            if (ids.isNotEmpty() && ids.all { it == planSheetId }) null else "format request touches sheetIds $ids, Plan is $planSheetId"
        }

        call.isPut || call.isAppend -> if (call.path.startsWith("/values/Plan!")) null else "write to ${call.path}"

        call.isDoneWrite -> {
            val ranges = Json.parseToJsonElement(call.body).jsonObject["data"]!!.jsonArray.map { it.jsonObject["range"]!!.jsonPrimitive.content }
            if (ranges.all { it.startsWith("Plan!") }) null else "done write to $ranges"
        }

        else -> "unmodelled write $call"
    }

    /** A new spreadsheet may only have the one `Plan` tab. */
    private fun createViolation(call: RecordedCall): String? {
        val titles = Json.parseToJsonElement(call.body).jsonObject["sheets"]?.jsonArray
            ?.map { it.jsonObject["properties"]!!.jsonObject["title"]!!.jsonPrimitive.content }
        return if (titles == listOf("Plan")) null else "create with tabs $titles"
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
