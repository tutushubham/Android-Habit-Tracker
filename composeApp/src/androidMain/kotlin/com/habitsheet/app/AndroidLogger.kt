package com.habitsheet.app

import android.util.Log
import com.habitsheet.platform.LogLevel
import com.habitsheet.platform.Logger
import com.habitsheet.platform.formatLogLine

/** Logcat only. Adds up to [MAX_FRAMES] stack frames (class and method names, never the exception message). */
class AndroidLogger : Logger {
    override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        val line = buildString {
            append(formatLogLine(message, throwable))
            throwable?.stackTrace?.take(MAX_FRAMES)?.forEach { append("\n    at ").append(it) }
        }
        val priority = when (level) {
            LogLevel.DEBUG -> Log.DEBUG
            LogLevel.INFO -> Log.INFO
            LogLevel.WARN -> Log.WARN
            LogLevel.ERROR -> Log.ERROR
        }
        Log.println(priority, "$TAG_PREFIX$tag", line)
    }

    companion object {
        private const val TAG_PREFIX = "HabitSheet/"
        private const val MAX_FRAMES = 8
        private val installed = java.util.concurrent.atomic.AtomicBoolean(false)

        /**
         * Logs uncaught exceptions (class names and frames only) and then hands over to the previous handler, so
         * the system crash dialog and Play vitals keep working. Nothing is sent anywhere.
         */
        fun installUncaughtExceptionLogger(logger: Logger) {
            if (!installed.compareAndSet(false, true)) return // Activity recreation must not chain the hook twice.
            val previous = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, error ->
                try {
                    logger.log(LogLevel.ERROR, "Crash", "Uncaught exception on thread ${thread.name}", error)
                } catch (_: Throwable) {
                    // Logging must never mask the original crash.
                }
                previous?.uncaughtException(thread, error)
            }
        }
    }
}
