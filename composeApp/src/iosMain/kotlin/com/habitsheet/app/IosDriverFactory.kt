package com.habitsheet.app

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.habitsheet.data.DriverFactory
import com.habitsheet.database.HabitsDatabase

class IosDriverFactory : DriverFactory {
    override fun createDriver(): SqlDriver = NativeSqliteDriver(
        schema = HabitsDatabase.Schema,
        name = "habit-sheet.db",
    )
}
