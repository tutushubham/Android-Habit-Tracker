package com.habitsheet.presentation

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

interface DateProvider {
    fun today(): LocalDate
    fun nowEpochMillis(): Long
}

object SystemDateProvider : DateProvider {
    override fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())
    override fun nowEpochMillis(): Long = Clock.System.now().toEpochMilliseconds()
}
