package com.habitsheet.presentation

import com.habitsheet.domain.backup.BackupSerializer
import com.habitsheet.domain.backup.BackupSettings
import com.habitsheet.domain.backup.BackupValidationException
import com.habitsheet.domain.backup.CsvGenerator
import com.habitsheet.domain.repository.HabitRepository
import com.habitsheet.platform.Logger
import com.habitsheet.platform.NoOpLogger
import com.habitsheet.platform.e
import com.habitsheet.platform.w
import com.habitsheet.platform.runCatchingCancellable
import com.habitsheet.ui.BackupService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerializationException

class BackupViewModel(
    private val repository: HabitRepository,
    private val backupService: BackupService,
    private val dateProvider: DateProvider = SystemDateProvider,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
    private val callbackDispatcher: CoroutineDispatcher = Dispatchers.Main,
    private val logger: Logger = NoOpLogger,
) {
    val usesClipboard: Boolean get() = backupService.usesClipboard

    /**
     * Exports a version 4 backup: all habit data plus theme and onboarding. The sheet link is included only when
     * [includeSheetLink] is true (default: no, so a shared backup file never reveals the person's sheet).
     */
    fun exportBackup(includeSheetLink: Boolean = false) {
        // UNDISPATCHED: starts on the caller's thread; the repository reads below do not normally suspend.
        scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val settings = runCatchingCancellable {
                BackupSettings(
                    themeMode = repository.getThemeMode(),
                    onboardingCompleted = repository.isOnboardingCompleted(),
                    sheetUrl = if (includeSheetLink) repository.getSheetUrl().ifBlank { null } else null,
                )
            }.onFailure { logger.w(TAG, "Reading settings for the backup failed; exporting data only", it) }.getOrNull()
            val json = BackupSerializer.serialize(repository.snapshot.value, dateProvider.nowEpochMillis(), settings)
            withContext(callbackDispatcher) { backupService.exportBackup(json) }
        }
    }

    fun exportCsv() {
        val snapshot = repository.snapshot.value
        val csv = CsvGenerator.generate(snapshot)
        backupService.exportCsv(csv)
    }

    fun clearAllData() {
        scope.launch {
            runCatchingCancellable { repository.clearAllData() }
                .onFailure { logger.e(TAG, "clearAllData failed", it) }
        }
    }

    /**
     * Restores any supported backup (version 1-4). Sheet sync state is always reset. A sheet link stored in a
     * version 4 backup replaces the current link only when [applySheetLink] is true (the caller asks the person).
     */
    fun importBackup(onSuccess: () -> Unit, onError: (String) -> Unit, applySheetLink: Boolean = false) {
        backupService.importBackup { json ->
            scope.launch {
                val failure = runCatchingCancellable {
                    val backup = BackupSerializer.parse(json)
                    repository.restoreFromSnapshot(backup.snapshot, backup.settings, applySheetLink)
                }.exceptionOrNull()
                failure?.let { logger.e(TAG, "importBackup failed", it) }

                withContext(callbackDispatcher) {
                    if (failure == null) {
                        onSuccess()
                    } else {
                        onError(failure.toImportMessage())
                    }
                }
            }
        }
    }

    fun close() {
        scope.cancel()
    }

    private companion object {
        const val TAG = "Backup"
    }
}

private fun Throwable.toImportMessage(): String = when (this) {
    is BackupValidationException -> message ?: "This backup contains invalid data."
    is SerializationException -> "This isn't a valid Habit Sheet backup file."
    else -> "The backup couldn't be restored. Your existing data was not changed."
}
