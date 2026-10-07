package com.habitsheet.ui

import com.habitsheet.presentation.UiText

/** How an export ended. [message] is shown to the person as is. */
sealed interface BackupResult {
    data class Success(val message: UiText) : BackupResult

    /** The person closed the file picker. Nothing was written and nothing needs saying. */
    data object Cancelled : BackupResult

    data class Failure(val message: UiText) : BackupResult
}

interface BackupService {
    val usesClipboard: Boolean get() = false

    /** Reports exactly once through [onResult], on the main thread, unless the process died while a picker was open. */
    fun exportBackup(json: String, onResult: (BackupResult) -> Unit)
    fun exportCsv(csv: String, onResult: (BackupResult) -> Unit)

    /**
     * Reads the backup text and calls [onImport] with the whole content, or [onFailure] with a user-readable
     * reason when it cannot be read. Neither is called if the person cancels the picker.
     */
    fun importBackup(onImport: (String) -> Unit, onFailure: (UiText) -> Unit)
}
