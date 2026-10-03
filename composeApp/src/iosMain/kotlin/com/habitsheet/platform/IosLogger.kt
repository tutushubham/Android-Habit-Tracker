package com.habitsheet.platform

import platform.Foundation.NSLog

/** Console / Xcode output only. The line is passed as an argument, never as the format string. */
class IosLogger : Logger {
    override fun log(level: LogLevel, tag: String, message: String, throwable: Throwable?) {
        NSLog("%@", "[HabitSheet/$tag] ${level.name} ${formatLogLine(message, throwable)}")
    }
}
