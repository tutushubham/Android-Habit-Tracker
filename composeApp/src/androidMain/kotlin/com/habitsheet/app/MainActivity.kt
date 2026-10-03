package com.habitsheet.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.habitsheet.AppGraph
import com.habitsheet.app.widget.HabitWidgetUpdater
import com.habitsheet.ui.HabitSheetApp
import kotlinx.coroutines.launch
import java.util.TimeZone

class MainActivity : ComponentActivity() {
    private lateinit var graph: AppGraph

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val logger = AndroidLogger()
        AndroidLogger.installUncaughtExceptionLogger(logger)
        graph = AppGraph(AndroidDriverFactory(applicationContext), AndroidSheetTokenProvider(this, logger), logger)
        val shareService = AndroidShareService(this)
        val backupService = AndroidBackupService(this, logger)
        setContent {
            HabitSheetApp(
                graph.monthViewModel,
                graph.manageHabitsViewModel,
                shareService,
                graph.settingsViewModel,
                backupService,
                graph.repository,
                AndroidVersionProvider(this),
            )
        }
    }

    private val clockChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // The JVM caches the default zone; drop it so the next lookup sees the new system zone.
            if (intent.action == Intent.ACTION_TIMEZONE_CHANGED) TimeZone.setDefault(null)
            graph.refreshToday()
            HabitWidgetUpdater.requestUpdate(applicationContext)
        }
    }

    override fun onStart() {
        super.onStart()
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_TIMEZONE_CHANGED)
            addAction(Intent.ACTION_DATE_CHANGED) // midnight
            addAction(Intent.ACTION_TIME_CHANGED) // the clock was set manually
        }
        ContextCompat.registerReceiver(this, clockChangedReceiver, filter, ContextCompat.RECEIVER_NOT_EXPORTED)
    }

    override fun onStop() {
        unregisterReceiver(clockChangedReceiver)
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        // Midnight or a zone change may have happened while the app was in the background.
        TimeZone.setDefault(null)
        graph.refreshToday()
        // Widget actions write to the same database while the activity is paused.
        // Refresh before the user continues so Compose never shows stale checks.
        lifecycleScope.launch {
            graph.repository.refresh()
            graph.syncOnForeground()
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
