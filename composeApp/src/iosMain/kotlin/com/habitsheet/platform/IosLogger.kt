package com.habitsheet.platform

import platform.Foundation.NSLog

/**
 * Console / Xcode output only. The line is the *format* (with every `%` escaped), never an argument: Kotlin/Native
 * does not bridge a `String` passed through the `%@` vararg of `NSLog` and crashes in `objc_opt_respondsToSelector`
 * (found on the first simulator run, which crashed the app at startup the first time anything was logged).
 */
class IosLogger : Logger {
    override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        NSLog("[HabitSheet/$tag] ${level.name} ${formatLogLine(message, throwable)}".replace("%", "%%"))
    }
}
