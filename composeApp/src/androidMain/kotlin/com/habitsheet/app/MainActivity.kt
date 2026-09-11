package com.habitsheet.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.lifecycleScope
import com.habitsheet.AppGraph
import com.habitsheet.app.widget.HabitWidgetUpdater
import com.habitsheet.ui.HabitSheetApp
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var graph: AppGraph

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        graph = AppGraph(AndroidDriverFactory(applicationContext))
        val shareService = AndroidShareService(this)
        setContent {
            HabitSheetApp(
                graph.monthViewModel,
                graph.manageHabitsViewModel,
                shareService,
                graph.settingsViewModel,
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // Widget actions write to the same database while the activity is paused.
        // Refresh before the user continues so Compose never shows stale checks.
        lifecycleScope.launch {
            graph.repository.refresh()
            HabitWidgetUpdater.requestUpdate(applicationContext)
        }
    }

    override fun onPause() {
        // Keep the launcher view in sync with edits made inside the app.
        HabitWidgetUpdater.requestUpdate(applicationContext)
        super.onPause()
    }

    override fun onDestroy() {
        graph.close()
        super.onDestroy()
    }
}
