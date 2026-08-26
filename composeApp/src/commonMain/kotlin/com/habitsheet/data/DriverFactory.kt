package com.habitsheet.data

import app.cash.sqldelight.db.SqlDriver

fun interface DriverFactory {
    fun createDriver(): SqlDriver
}
