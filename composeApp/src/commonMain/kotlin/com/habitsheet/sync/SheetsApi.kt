package com.habitsheet.sync

import com.habitsheet.platform.Logger
import com.habitsheet.platform.NoOpLogger
import com.habitsheet.platform.w
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.request.HttpRequestBuilder
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.serialization.json.*
import kotlin.math.min
import kotlin.random.Random

/** Network timeouts for the real client; tests inject their own mock-engine client. */
fun createSheetsHttpClient(): HttpClient = HttpClient {
    install(HttpTimeout) {
        connectTimeoutMillis = 15_000
        socketTimeoutMillis = 30_000
        requestTimeoutMillis = 45_000
    }
}

/**
 * Bounded exponential backoff with jitter. Attempt n (1-based) waits a random time between half of and the full
 * `min(maxDelay, base * 2^(n-1))`. A `Retry-After` from Google replaces the computed wait; if it is longer than
 * [maxRetryAfterMillis] the request is not retried at all and the error carries the wait instead.
 */
class RetryPolicy(
    val maxAttempts: Int = 4,
    private val baseDelayMillis: Long = 500,
    private val maxDelayMillis: Long = 8_000,
    val maxRetryAfterMillis: Long = 30_000,
    private val random: () -> Double = { Random.nextDouble() },
    internal val sleep: suspend (millis: Long) -> Unit = { delay(it) },
) {
    fun backoffMillis(failedAttempts: Int): Long {
        val cap = min(maxDelayMillis, baseDelayMillis shl (failedAttempts - 1).coerceIn(0, 20))
        return cap / 2 + (random() * (cap / 2)).toLong()
    }

    companion object {
        val NONE = RetryPolicy(maxAttempts = 1)
    }
}

private const val TAG = "SheetsApi"

/** HTTP only: talks to the Sheets v4 REST API for one spreadsheet. Parsing lives in [PlanTable]. */
internal class SheetsApi(
    private val client: HttpClient,
    id: String,
    private val token: String,
    private val retry: RetryPolicy = RetryPolicy(),
    private val logger: Logger = NoOpLogger,
) {
    private val base = "https://sheets.googleapis.com/v4/spreadsheets/$id"

    private fun HttpRequestBuilder.auth() {
        headers { append(HttpHeaders.Authorization, "Bearer $token") }
    }

    /**
     * Sends a request and returns the 2xx body, or throws a [SyncError].
     *
     * Retried (up to [RetryPolicy.maxAttempts]): 429, 503 always; 500/502/504 and connectivity failures only when
     * [idempotent], because the server may already have acted on a request whose reply was lost (append would
     * duplicate rows).
     */
    private suspend fun call(idempotent: Boolean, request: suspend () -> HttpResponse): String {
        var attempt = 0
        while (true) {
            attempt++
            var failure: SyncError
            var retryable: Boolean
            var retryAfterMillis: Long? = null
            try {
                val response = request()
                val body = response.bodyAsText()
                val status = response.status.value
                if (status in 200..299) return body
                retryAfterMillis = response.headers[HttpHeaders.RetryAfter]?.trim()?.toLongOrNull()?.let { it * 1000 }
                failure = mapStatus(status, body, retryAfterMillis)
                logger.w(TAG, "Sheets request failed: HTTP $status (attempt $attempt of ${retry.maxAttempts})")
                retryable = failure is SyncError.RateLimited || status == 503 || (idempotent && status in 500..599)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failure = e.toSyncError()
                logger.w(TAG, "Sheets request failed before a response (attempt $attempt of ${retry.maxAttempts})", e)
                retryable = idempotent && failure is SyncError.Offline
            }
            if (!retryable || attempt >= retry.maxAttempts) throw failure
            val wait = retryAfterMillis ?: retry.backoffMillis(attempt)
            if (wait > retry.maxRetryAfterMillis) throw failure
            retry.sleep(wait)
        }
    }

    private fun mapStatus(status: Int, body: String, retryAfterMillis: Long?): SyncError = when {
        status == 401 -> SyncError.AuthExpired()

        status == 403 && ("rateLimitExceeded" in body || "RATE_LIMIT_EXCEEDED" in body) ->
            SyncError.RateLimited(retryAfterMillis?.div(1000))

        status == 403 -> SyncError.AccessDenied()

        status == 404 -> SyncError.NotFound()

        status == 429 -> SyncError.RateLimited(retryAfterMillis?.div(1000))

        else -> SyncError.Unknown(status = status)
    }

    suspend fun hasPlanTab(): Boolean {
        val body = call(idempotent = true) {
            client.get(base) {
                auth()
                url { parameters.append("fields", "sheets(properties(title))") }
            }
        }
        val data = Json.parseToJsonElement(body).jsonObject
        return data["sheets"]?.jsonArray?.any { it.jsonObject["properties"]?.jsonObject?.get("title")?.jsonPrimitive?.content == "Plan" } == true
    }

    suspend fun createPlanTab(): Int? {
        val body = call(idempotent = false) {
            client.post("$base:batchUpdate") {
                auth()
                contentType(ContentType.Application.Json)
                setBody("""{"requests":[{"addSheet":{"properties":{"title":"Plan","gridProperties":{"frozenRowCount":1}}}}]}""")
            }
        }
        val data = Json.parseToJsonElement(body).jsonObject
        return data["replies"]?.jsonArray?.firstOrNull()?.jsonObject?.get("addSheet")?.jsonObject
            ?.get("properties")?.jsonObject?.get("sheetId")?.jsonPrimitive?.intOrNull
    }

    suspend fun formatPlanTab(sheetId: Int) {
        val body = """{"requests":[
          {"setDataValidation":{"range":{"sheetId":$sheetId,"startRowIndex":1,"startColumnIndex":4,"endColumnIndex":6},"rule":{"condition":{"type":"BOOLEAN"},"strict":true,"showCustomUi":true}}},
          {"repeatCell":{"range":{"sheetId":$sheetId,"startRowIndex":0,"endRowIndex":1,"startColumnIndex":0,"endColumnIndex":6},"cell":{"userEnteredFormat":{"backgroundColor":{"red":0.08,"green":0.28,"blue":0.20},"textFormat":{"foregroundColor":{"red":1,"green":1,"blue":1},"bold":true}}},"fields":"userEnteredFormat"}},
          {"updateDimensionProperties":{"range":{"sheetId":$sheetId,"dimension":"COLUMNS","startIndex":3,"endIndex":4},"properties":{"pixelSize":420},"fields":"pixelSize"}}
        ]}"""
        call(idempotent = true) {
            client.post("$base:batchUpdate") {
                auth()
                contentType(ContentType.Application.Json)
                setBody(body)
            }
        }
    }

    suspend fun readTable(): List<SheetPlanRow>? {
        val body = call(idempotent = true) {
            client.get("$base/values/Plan!A:H") {
                auth()
                url { parameters.append("valueRenderOption", "UNFORMATTED_VALUE") }
            }
        }
        val values = Json.parseToJsonElement(body).jsonObject["values"]?.jsonArray ?: return null
        return parsePlanTable(values)
    }

    suspend fun putTable(rows: List<SheetPlanRow>) {
        val values = buildJsonArray {
            add(buildJsonArray { PlanTable.HEADER.forEach { add(it) } })
            rows.forEach { add(it.asValues()) }
        }
        val payload = buildJsonObject { put("values", values) }.toString()
        call(idempotent = true) {
            client.put("$base/values/Plan!A1:F${rows.size + 1}") {
                auth()
                contentType(ContentType.Application.Json)
                url { parameters.append("valueInputOption", "RAW") }
                setBody(payload)
            }
        }
    }

    suspend fun appendRows(rows: List<SheetPlanRow>) {
        val payload = buildJsonObject {
            put("values", buildJsonArray { rows.forEach { add(it.asValues()) } })
        }.toString()
        call(idempotent = false) {
            client.post("$base/values/Plan!A:F:append") {
                auth()
                contentType(ContentType.Application.Json)
                url {
                    parameters.append("valueInputOption", "RAW")
                    parameters.append("insertDataOption", "INSERT_ROWS")
                }
                setBody(payload)
            }
        }
    }

    suspend fun writeDone(updates: List<DoneUpload>) {
        val payload = buildJsonObject {
            put("valueInputOption", "RAW")
            put(
                "data",
                buildJsonArray {
                    updates.forEach { update ->
                        add(
                            buildJsonObject {
                                put("range", "Plan!${update.column}${update.sheetRow}")
                                put("values", buildJsonArray { add(buildJsonArray { add(update.done) }) })
                            },
                        )
                    }
                },
            )
        }.toString()
        call(idempotent = true) {
            client.post("$base/values:batchUpdate") {
                auth()
                contentType(ContentType.Application.Json)
                setBody(payload)
            }
        }
    }
}
