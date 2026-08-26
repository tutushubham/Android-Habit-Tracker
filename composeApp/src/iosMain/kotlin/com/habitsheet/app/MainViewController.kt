package com.habitsheet.app

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.window.ComposeUIViewController
import com.habitsheet.AppGraph
import com.habitsheet.domain.calculation.DailyShareSummary
import com.habitsheet.ui.BackupService
import com.habitsheet.ui.HabitSheetApp
import com.habitsheet.ui.ShareService

fun MainViewController() = ComposeUIViewController {
    val backupService = remember {
        object : BackupService {
            override fun exportBackup(json: String) {
                // Not implemented for iOS yet
            }
            override fun exportCsv(csv: String) {
                // Not implemented for iOS yet
            }
            override fun importBackup(onImport: (String) -> Unit) {
                // Not implemented for iOS yet
            }
        }
    }
    val graph = remember { AppGraph(IosDriverFactory(), backupService, IosVersionProvider()) }
    val shareService = remember {
        object : ShareService {
            override fun shareDailySummary(summary: DailyShareSummary) {
                // Not implemented for iOS yet
            }
        }
    }
    DisposableEffect(graph) {
        onDispose(graph::close)
    }
    HabitSheetApp(
        graph.monthViewModel,
        graph.manageHabitsViewModel,
        shareService,
        graph.backupViewModel,
        graph.settingsViewModel,
        graph.versionProvider
    )
}
