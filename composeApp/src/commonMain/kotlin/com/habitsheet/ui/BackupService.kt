package com.habitsheet.ui

import com.habitsheet.domain.model.HabitSnapshot

interface BackupService {
    fun exportBackup(json: String)
    fun exportCsv(csv: String)
    fun importBackup(onImport: (String) -> Unit)
}
