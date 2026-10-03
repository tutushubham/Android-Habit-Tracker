package com.habitsheet.platform

import kotlinx.coroutines.CancellationException

enum class LogLevel { DEBUG, INFO, WARN, ERROR }

/**
 * Local-only diagnostics (logcat / Console). Nothing leaves the device.
 *
 * Privacy rules for every call site and implementation:
 *  - [message] is a fixed description of what failed ("saveCategory failed"), never user text: no sheet URLs or
 *    ids, tokens, habit/category names or session text.
 *  - A [throwable] is reduced to its class names ([describeForLog]) because exception messages can echo request
 *    URLs or JSON fragments containing user text. Implementations may add stack frames (class and method names
 *    only), never the exception message.
 */
interface Logger {
    fun log(level: LogLevel, tag: String, message: String, throwable: Throwable? = null)
}

object NoOpLogger : Logger {
    override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) = Unit
}

fun Logger.d(tag: String, message: String, throwable: Throwable? = null) = log(LogLevel.DEBUG, tag, message, throwable)
fun Logger.i(tag: String, message: String, throwable: Throwable? = null) = log(LogLevel.INFO, tag, message, throwable)
fun Logger.w(tag: String, message: String, throwable: Throwable? = null) = log(LogLevel.WARN, tag, message, throwable)
fun Logger.e(tag: String, message: String, throwable: Throwable? = null) = log(LogLevel.ERROR, tag, message, throwable)

/** `IllegalStateException <- SQLiteException`: the exception and its causes by class name only. */
fun Throwable.describeForLog(): String = buildString {
    var current: Throwable? = this@describeForLog
    var depth = 0
    while (current != null && depth < 5) {
        if (depth > 0) append(" <- ")
        append(current::class.simpleName ?: "Throwable")
        current = current.cause?.takeIf { it !== current }
        depth++
    }
}

/** Message plus the redacted throwable summary, as platform loggers print it. */
fun formatLogLine(message: String, throwable: Throwable?): String =
    if (throwable == null) message else "$message [${throwable.describeForLog()}]"

/**
 * Like `runCatching`, but a [CancellationException] is rethrown so cancelling a coroutine is never mistaken for
 * a failure. Only [Exception]s are captured (Errors such as OutOfMemoryError still propagate).
 */
inline fun <T> runCatchingCancellable(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }
