package com.habitsheet.domain.model

/** A local notification once a day at [minuteOfDay] (0 = midnight) in the device's time zone. Kept per device, not in backups. */
data class DailyReminder(val minuteOfDay: Int) {
    init {
        require(minuteOfDay in 0 until MINUTES_PER_DAY) { "minute of day out of range: $minuteOfDay" }
    }

    val hour: Int get() = minuteOfDay / MINUTES_PER_HOUR
    val minute: Int get() = minuteOfDay % MINUTES_PER_HOUR

    companion object {
        private const val MINUTES_PER_HOUR = 60
        private const val MINUTES_PER_DAY = 24 * MINUTES_PER_HOUR
    }
}
