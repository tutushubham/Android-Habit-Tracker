package com.habitsheet.presentation

import androidx.lifecycle.ViewModel
import com.habitsheet.domain.backup.BackupSerializer
import com.habitsheet.domain.backup.BackupValidationException
import com.habitsheet.domain.backup.CsvGenerator
import com.habitsheet.domain.backup.ParsedBackup
import com.habitsheet.domain.repository.BackupStore
import com.habitsheet.platform.Logger
import com.habitsheet.platform.NoOpLogger
import com.habitsheet.platform.e
import com.habitsheet.platform.runCatchingCancellable
import com.habitsheet.platform.w
import com.habitsheet.presentation.UiText
import com.habitsheet.resources.*
import com.habitsheet.ui.BackupResult
import com.habitsheet.ui.BackupService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException

/** What a chosen backup file contains, shown before anything is replaced. Created only by [BackupViewModel]. */
class BackupPreview internal constructor(
    val incoming: DataSummary,
    val hasSheetLink: Boolean,
    internal val backup: ParsedBackup,
)

class BackupViewModel(
    private val repository: BackupStore,
    private val backupService: BackupService,
    private val dateProvider: DateProvider = SystemDateProvider,
    scope: CoroutineScope? = null,
    private val callbackDispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val logger: Logger = NoOpLogger,
    /** Called on the callback dispatcher after a reset or restore succeeded (settings screens re-read storage). */
    private val onDataReplaced: () -> Unit = {},
) : ViewModel() {
    /** Defaults to the ViewModel's own background scope; tests inject theirs. */
    private val scope: CoroutineScope = scope ?: backgroundScope()

    /** What would be lost by a reset or an import right now. */
    fun currentDataSummary(): DataSummary = DataSummary.of(repository.snapshot.value)

    val usesClipboard: Boolean get() = backupService.usesClipboard

    /**
     * Exports a version 4 backup: all habit data plus theme and onboarding. The sheet link is included only when
     * [includeSheetLink] is true (default: no, so a shared backup file never reveals the person's sheet).
     */
    fun exportBackup(includeSheetLink: Boolean = false, onResult: (BackupResult) -> Unit = {}) {
        // UNDISPATCHED: starts on the caller's thread; the repository reads below do not normally suspend.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val settings = runCatchingCancellable { repository.backupSettings(includeSheetLink) }.onFailure { logger.w(TAG, "Reading settings for the backup failed; exporting data only", it) }.getOrNull()
            val json = BackupSerializer.serialize(repository.snapshot.value, dateProvider.nowEpochMillis(), settings)
            withContext(callbackDispatcher) { backupService.exportBackup(json, onResult) }
        }
    }

    fun exportCsv(onResult: (BackupResult) -> Unit = {}) {
        val snapshot = repository.snapshot.value
        val csv = CsvGenerator.generate(snapshot)
        backupService.exportCsv(csv, onResult)
    }

    fun clearAllData() {
        scope.launch {
            runCatchingCancellable { repository.clearAllData() }
                .onSuccess { withContext(callbackDispatcher) { onDataReplaced() } }
                .onFailure { logger.e(TAG, "clearAllData failed", it) }
        }
    }

    /** True when a sheet link is saved on this device, so the export screen can offer to include it. */
    suspend fun hasSheetLink(): Boolean =
        runCatchingCancellable { !repository.backupSettings(includeSheetLink = true).sheetUrl.isNullOrBlank() }.getOrDefault(false)

    /**
     * First half of an import: lets the person choose a file, then reads and fully validates it (parse, checksum,
     * validator) WITHOUT changing anything, and reports what it contains through [onPreview]. Nothing is replaced
     * until [restore] is called with the preview.
     */
    fun pickBackup(onPreview: (BackupPreview) -> Unit, onError: (UiText) -> Unit) {
        backupService.importBackup(onFailure = { message ->
            scope.launch { withContext(callbackDispatcher) { onError(message) } }
        }, onImport = { json ->
            scope.launch {
                val result = runCatchingCancellable { BackupSerializer.parse(json) }
                result.exceptionOrNull()?.let { logger.e(TAG, "reading the backup failed", it) }
                withContext(callbackDispatcher) {
                    result.fold(
                        onSuccess = { backup ->
                            onPreview(
                                BackupPreview(
                                    incoming = DataSummary.of(backup.snapshot),
                                    hasSheetLink = !backup.settings?.sheetUrl.isNullOrBlank(),
                                    backup = backup,
                                ),
                            )
                        },
                        onFailure = { onError(it.toImportMessage()) },
                    )
                }
            }
        })
    }

    /**
     * Second half of an import: replaces the data with the previewed backup in one transaction. Sheet sync state is
     * always reset. A sheet link stored in the backup replaces the current link only when [applySheetLink] is true.
     */
    fun restore(preview: BackupPreview, onSuccess: () -> Unit, onError: (UiText) -> Unit, applySheetLink: Boolean = false) {
        scope.launch {
            val failure = runCatchingCancellable {
                repository.restoreFromSnapshot(preview.backup.snapshot, preview.backup.settings, applySheetLink)
            }.exceptionOrNull()
            failure?.let { logger.e(TAG, "importBackup failed", it) }
            withContext(callbackDispatcher) {
                if (failure == null) {
                    onDataReplaced()
                    onSuccess()
                } else {
                    onError(failure.toImportMessage())
                }
            }
        }
    }

    /**
     * Pick and restore in one step (no preview). Restores any supported backup (version 1-4). A sheet link stored
     * in a version 4 backup replaces the current link only when [applySheetLink] is true.
     */
    fun importBackup(onSuccess: () -> Unit, onError: (UiText) -> Unit, applySheetLink: Boolean = false) {
        pickBackup(onPreview = { restore(it, onSuccess, onError, applySheetLink) }, onError = onError)
    }

    private companion object {
        const val TAG = "Backup"
    }
}

private fun Throwable.toImportMessage(): UiText = when (this) {
    // The validator's own English diagnostic is shown as it is.
    is BackupValidationException ->
        message?.let { UiText.of(Res.string.backup_err_invalid, UiText.Raw(it)) } ?: UiText.of(Res.string.backup_err_invalid_default)

    is SerializationException -> UiText.of(Res.string.backup_err_not_backup)

    else -> UiText.of(Res.string.backup_err_restore_failed)
}
