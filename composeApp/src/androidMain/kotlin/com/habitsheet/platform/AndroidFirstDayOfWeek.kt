package com.habitsheet.platform

import kotlinx.datetime.DayOfWeek
import java.util.Calendar

actual fun localeFirstDayOfWeek(): DayOfWeek = dayOfWeekFromSundayBased(Calendar.getInstance().firstDayOfWeek)
