package com.habitsheet.domain.model

/** Only Google Sheets document links are accepted; the tab is selected by name. */
object SheetLink {
    private val pattern = Regex("^https://docs\\.google\\.com/spreadsheets/d/([A-Za-z0-9_-]+)(?:/|[?#]|$)")

    fun canonicalize(input: String): String? {
        val id = pattern.find(input.trim())?.groupValues?.get(1) ?: return null
        return "https://docs.google.com/spreadsheets/d/$id/edit"
    }

    fun spreadsheetId(input: String): String? = pattern.find(input.trim())?.groupValues?.get(1)
}
