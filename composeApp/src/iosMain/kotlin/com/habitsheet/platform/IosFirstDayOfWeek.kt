package com.habitsheet.platform

import kotlinx.datetime.DayOfWeek
import platform.Foundation.NSCalendar

actual fun localeFirstDayOfWeek(): DayOfWeek = dayOfWeekFromSundayBased(NSCalendar.currentCalendar.firstWeekday.toInt())
