package com.habitsheet.presentation

import com.habitsheet.domain.backup.BackupSerializer
import com.habitsheet.domain.backup.BackupValidationException
import com.habitsheet.domain.backup.CsvGenerator
import com.habitsheet.domain.repository.HabitRepository
import com.habitsheet.ui.BackupService
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CancellationException
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
) {

    fun exportBackup() {
        val snapshot = repository.snapshot.value
        val json = BackupSerializer.serialize(snapshot, dateProvider.nowEpochMillis())
        backupService.exportBackup(json)
    }

    fun exportCsv() {
        val snapshot = repository.snapshot.value
        val csv = CsvGenerator.generate(snapshot)
        backupService.exportCsv(csv)
    }

    fun clearAllData() {
        scope.launch {
            repository.clearAllData()
        }
    }

    fun importBackup(onSuccess: () -> Unit, onError: (String) -> Unit) {
        backupService.importBackup { json ->
            scope.launch {
                val failure = try {
                    val snapshot = BackupSerializer.deserialize(json)
                    repository.restoreFromSnapshot(snapshot)
                    null
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    e
                }

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
}

private fun Exception.toImportMessage(): String = when (this) {
    is BackupValidationException -> message ?: "This backup contains invalid data."
    is SerializationException -> "This isn't a valid Habit Sheet backup file."
    else -> "The backup couldn't be restored. Your existing data was not changed."
}
