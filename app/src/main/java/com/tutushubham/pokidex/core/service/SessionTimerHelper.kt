package com.tutushubham.pokidex.core.service

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import androidx.core.content.ContextCompat

/**
 * Helper class for managing SessionTimerService and receiving ticks
 */
object SessionTimerHelper {

    fun startTimer(context: Context, sessionId: String) {
        val intent = SessionTimerService.createStartIntent(context, sessionId)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
    }

    fun stopTimer(context: Context) {
        val intent = SessionTimerService.createStopIntent(context)
        context.stopService(intent)
    }

    /**
     * Creates a BroadcastReceiver that forwards ticks to the ViewModel
     */
    fun createTickReceiver(
        onTick: (Int) -> Unit
    ): BroadcastReceiver {
        return object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val minutes = intent?.getIntExtra(
                    SessionTimerService.EXTRA_ELAPSED_MINUTES,
                    0
                ) ?: return
                onTick(minutes)
            }
        }
    }

    /**
     * Register the tick receiver
     */
    fun registerTickReceiver(
        context: Context,
        receiver: BroadcastReceiver
    ) {
        val filter = IntentFilter(SessionTimerService.ACTION_TICK)
        ContextCompat.registerReceiver(
            context,
            receiver,
            filter,
            ContextCompat.RECEIVER_NOT_EXPORTED
        )
    }

    /**
     * Unregister the tick receiver
     */
    fun unregisterTickReceiver(
        context: Context,
        receiver: BroadcastReceiver
    ) {
        try {
            context.unregisterReceiver(receiver)
        } catch (e: IllegalArgumentException) {
            // Receiver not registered, ignore
        }
    }
}
