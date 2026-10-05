package com.habitsheet.app

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.habitsheet.AppGraph
import com.habitsheet.AppStartup
import com.habitsheet.StartupState
import com.habitsheet.app.widget.HabitWidgetUpdater
import com.habitsheet.ui.HabitSheetApp
import com.habitsheet.ui.HabitSheetTheme
import com.habitsheet.ui.StartupFailureScreen
import kotlinx.coroutines.launch
import java.util.TimeZone

class MainActivity : ComponentActivity() {
    /** Set only while the database opened; every lifecycle hook below tolerates null (startup failure screen). */
    private var graph: AppGraph? = null
    private var startup by mutableStateOf<StartupState?>(null)

    private lateinit var logger: AndroidLogger
    private lateinit var tokenProvider: AndroidSheetTokenProvider

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        logger = AndroidLogger()
        AndroidLogger.installUncaughtExceptionLogger(logger)
        // Everything that registers an activity-result launcher is created once, here, whether or not the database opens.
        tokenProvider = AndroidSheetTokenProvider(this, logger)
        val shareService = AndroidShareService(this)
        val backupService = AndroidBackupService(this, logger)
        val recovery = AndroidStartupRecovery(this, logger)
        start()
        setContent {
            when (val state = startup) {
                is StartupState.Ready -> HabitSheetApp(
                    state.graph,
                    shareService,
                    backupService,
                    AndroidVersionProvider(this),
                )

                is StartupState.Failed -> HabitSheetTheme { StartupFailureScreen(state.cause, recovery, onRetry = ::start) }

                null -> Unit
            }
        }
    }

    /** Opens the database and builds the app. On failure the recovery screen is shown instead of crashing. */
    private fun start() {
        val result = AppStartup.create(AndroidDriverFactory(applicationContext), tokenProvider, logger)
        graph = (result as? StartupState.Ready)?.graph
        startup = result
        if (graph != null && lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) onForeground()
    }

    private val clockChangedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            // The JVM caches the default zone; drop it so the next lookup sees the new system zone.
            if (intent.action == Intent.ACTION_TIMEZONE_CHANGED) TimeZone.setDefault(null)
            graph?.refreshToday()
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
        onForeground()
    }

    private fun onForeground() {
        val graph = graph ?: return
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
        if (graph != null) HabitWidgetUpdater.requestUpdate(applicationContext)
        super.onPause()
    }

    override fun onDestroy() {
        graph?.close()
        super.onDestroy()
    }
}
