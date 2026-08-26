package com.habitsheet.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.habitsheet.AppGraph
import com.habitsheet.ui.HabitSheetApp

class MainActivity : ComponentActivity() {
    private lateinit var graph: AppGraph

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val backupService = AndroidBackupService(this)
        val versionProvider = AndroidVersionProvider(applicationContext)
        graph = AppGraph(AndroidDriverFactory(applicationContext), backupService, versionProvider)
        val shareService = AndroidShareService(this)
        setContent {
            HabitSheetApp(
                graph.monthViewModel,
                graph.manageHabitsViewModel,
                shareService,
                graph.backupViewModel,
                graph.settingsViewModel,
                graph.versionProvider
            )
        }
    }

    override fun onDestroy() {
        graph.close()
        super.onDestroy()
    }
}
