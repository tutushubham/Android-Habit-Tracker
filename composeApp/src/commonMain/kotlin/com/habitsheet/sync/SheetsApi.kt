package com.habitsheet.sync

import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.headers
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.contentType
import kotlinx.serialization.json.*

/** HTTP only: talks to the Sheets v4 REST API for one spreadsheet. Parsing lives in [PlanTable]. */
internal class SheetsApi(private val client: HttpClient, private val id: String, private val token: String) {
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
            add(buildJsonArray { PlanTable.HEADER.forEach { add(it) } })
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
    suspend fun writeDone(updates: List<DoneUpload>) {
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

