package com.tutushubham.pokidex.core.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.tutushubham.pokidex.MainActivity
import com.tutushubham.pokidex.core.data.local.preferences.TimerPreferences
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed class SessionTimerCommand {
    data class Start(val sessionId: String) : SessionTimerCommand()
    data object Stop : SessionTimerCommand()
}

class SessionTimerService : Service() {

    private var startTimeMillis: Long = 0L
    private var tickerJob: Job? = null
    private var activeSessionId: String? = null
    private val serviceScope = CoroutineScope(Dispatchers.Default)
    private lateinit var timerPreferences: TimerPreferences
    private var isRestoring = false

    companion object {
        private const val TAG = "SessionTimerService"
        private const val CHANNEL_ID = "session_timer_channel"
        private const val NOTIFICATION_ID = 1
        const val ACTION_START = "com.tutushubham.pokidex.ACTION_START"
        const val ACTION_RESUME = "com.tutushubham.pokidex.ACTION_RESUME"
        const val ACTION_STOP = "com.tutushubham.pokidex.ACTION_STOP"
        const val ACTION_TICK = "com.tutushubham.pokidex.ACTION_TICK"
        const val EXTRA_ELAPSED_MINUTES = "elapsed_minutes"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_ELAPSED_OFFSET = "elapsed_offset"

        fun createStartIntent(context: Context, sessionId: String): Intent {
            return Intent(context, SessionTimerService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_SESSION_ID, sessionId)
            }
        }

        fun createResumeIntent(context: Context, sessionId: String, elapsedMinutes: Int): Intent {
            return Intent(context, SessionTimerService::class.java).apply {
                action = ACTION_RESUME
                putExtra(EXTRA_SESSION_ID, sessionId)
                putExtra(EXTRA_ELAPSED_OFFSET, elapsedMinutes)
            }
        }

        fun createStopIntent(context: Context): Intent {
            return Intent(context, SessionTimerService::class.java).apply {
                action = ACTION_STOP
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        timerPreferences = TimerPreferences(applicationContext)
        createNotificationChannel()
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {
        when (intent?.action) {
            ACTION_START -> {
                val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
                sessionId?.let {
                    startTimeMillis = 0L
                    startTimer(it)
                }
            }
            ACTION_RESUME -> {
                val sessionId = intent.getStringExtra(EXTRA_SESSION_ID)
                val elapsedOffset = intent.getIntExtra(EXTRA_ELAPSED_OFFSET, 0)
                sessionId?.let {
                    startTimeMillis = System.currentTimeMillis() - (elapsedOffset * 60_000L)
                    activeSessionId = it
                    serviceScope.launch {
                        timerPreferences.saveTimerState(it, startTimeMillis)
                    }
                    startTimerFromExisting(it)
                }
            }
            ACTION_STOP -> stopTimer()
            null -> {
                // Service restarted by system - restore timer state from DataStore asynchronously
                // Avoid blocking main thread with runBlocking
                if (!isRestoring) {
                    isRestoring = true
                    serviceScope.launch {
                        val restored = restoreTimerState()
                        restored?.let { (sessionId, startTime) ->
                            activeSessionId = sessionId
                            startTimeMillis = startTime
                            startTimer(sessionId)
                        }
                        isRestoring = false
                    }
                }
            }
        }

        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Session Timer",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows active session timer"
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun startTimer(sessionId: String) {
        if (startTimeMillis == 0L) {
            startTimeMillis = System.currentTimeMillis()
            activeSessionId = sessionId
            serviceScope.launch {
                timerPreferences.saveTimerState(sessionId, startTimeMillis)
            }
        }
        startTimerFromExisting(sessionId)
    }

    private fun startTimerFromExisting(sessionId: String) {
        val initialElapsedMinutes = if (startTimeMillis > 0) {
            ((System.currentTimeMillis() - startTimeMillis) / 60000).toInt()
        } else {
            0
        }

        startForeground(NOTIFICATION_ID, buildNotification(initialElapsedMinutes, sessionId))

        tickerJob?.cancel()
        tickerJob = serviceScope.launch {
            while (isActive) {
                val elapsedMinutes =
                    ((System.currentTimeMillis() - startTimeMillis) / 60000).toInt()

                sendTick(elapsedMinutes)
                updateNotification(elapsedMinutes, sessionId)

                delay(60_000)
            }
        }
    }

    private fun stopTimer() {
        tickerJob?.cancel()
        tickerJob = null
        
        // Clear persisted state
        serviceScope.launch {
            timerPreferences.clearTimerState()
        }
        
        activeSessionId = null
        startTimeMillis = 0L
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private suspend fun restoreTimerState(): Pair<String, Long>? {
        val id = timerPreferences.getActiveSessionId()
        val time = timerPreferences.getStartTimeMillis()
        return if (id != null && time != null && time > 0) id to time else null
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    private fun sendTick(minutes: Int) {
        val intent = Intent(ACTION_TICK).apply {
            putExtra(EXTRA_ELAPSED_MINUTES, minutes)
            setPackage(packageName) // Scope to this app only for security
        }
        sendBroadcast(intent)
    }

    private fun updateNotification(minutes: Int, sessionId: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.notify(
            NOTIFICATION_ID,
            buildNotification(minutes, sessionId)
        )
    }

    private fun buildNotification(minutes: Int, sessionId: String): Notification {
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this,
            0,
            createStopIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopAction = NotificationCompat.Action(
            android.R.drawable.ic_media_pause,
            "Stop",
            stopIntent
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Session in progress")
            .setContentText("$minutes min elapsed")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .addAction(stopAction)
            .build()
    }
}
