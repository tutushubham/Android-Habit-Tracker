package com.habitsheet.app

import android.content.Context
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.habitsheet.data.DriverFactory
import com.habitsheet.database.HabitsDatabase

class AndroidDriverFactory(private val context: Context) : DriverFactory {
    override fun createDriver(): SqlDriver = AndroidSqliteDriver(
        schema = HabitsDatabase.Schema,
        context = context,
        name = "habit-sheet.db",
    )
}
