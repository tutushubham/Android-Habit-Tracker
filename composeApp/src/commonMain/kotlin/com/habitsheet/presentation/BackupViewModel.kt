package com.habitsheet.presentation

import com.habitsheet.domain.backup.BackupSerializer
import com.habitsheet.domain.backup.CsvGenerator
import com.habitsheet.domain.repository.HabitRepository
import com.habitsheet.ui.BackupService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class BackupViewModel(
    private val repository: HabitRepository,
    private val backupService: BackupService,
    private val dateProvider: DateProvider = SystemDateProvider,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
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
                try {
                    val snapshot = BackupSerializer.deserialize(json)
                    repository.restoreFromSnapshot(snapshot)
                    launch(Dispatchers.Main) { onSuccess() }
                } catch (e: Exception) {
                    launch(Dispatchers.Main) { onError(e.message ?: "Invalid backup file") }
                }
            }
        }
    }

    fun close() {
        scope.cancel()
    }
}
