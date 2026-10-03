package com.habitsheet.app

import android.content.Context
import androidx.sqlite.db.SupportSQLiteDatabase
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.habitsheet.data.DriverFactory
import com.habitsheet.database.HabitsDatabase

class AndroidDriverFactory(private val context: Context) : DriverFactory {
    override fun createDriver(): SqlDriver = AndroidSqliteDriver(
        schema = HabitsDatabase.Schema,
        context = context,
        name = DATABASE_NAME,
        callback = object : AndroidSqliteDriver.Callback(HabitsDatabase.Schema) {
            /**
             * The default behaviour of Android's SQLite helper is to DELETE a database it considers corrupt and
             * silently create an empty one. For a habit history that is the worst outcome, so the file is left
             * alone: the open fails, the startup failure screen appears and the person chooses what happens.
             */
            override fun onCorruption(db: SupportSQLiteDatabase) = Unit
        },
    )

    companion object {
        const val DATABASE_NAME = "habit-sheet.db"
    }
}
