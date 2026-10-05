package com.habitsheet.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.habitsheet.database.HabitsDatabase
import kotlin.test.Test
import kotlin.test.assertEquals

class PlanMigrationTest {
    @Test
    fun existingPlanAndCompletionKeepTheirIdentityAfterMigration() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            driver.execute(
                null,
                """CREATE TABLE dailyHabitEntity (
                id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, category_id TEXT,
                monthly_goal INTEGER NOT NULL, display_order INTEGER NOT NULL,
                active INTEGER NOT NULL, created_on TEXT NOT NULL, archived_on TEXT,
                created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL
            )""",
                0,
            )
            driver.execute(null, "INSERT INTO dailyHabitEntity VALUES ('run','Run',NULL,12,0,1,'2026-10-01',NULL,1,1)", 0)
            driver.execute(
                null,
                """CREATE TABLE dayPlanEntity (
                habit_id TEXT NOT NULL, date TEXT NOT NULL, detail TEXT NOT NULL,
                skipped INTEGER NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY(habit_id,date)
            )""",
                0,
            )
            driver.execute(null, "INSERT INTO dayPlanEntity VALUES ('run','2026-10-01','Easy 6 km',0,1)", 0)
            driver.execute(
                null,
                """CREATE TABLE dailyCompletionEntity (
                habit_id TEXT NOT NULL, date TEXT NOT NULL, completed INTEGER NOT NULL,
                updated_at INTEGER NOT NULL, PRIMARY KEY(habit_id,date)
            )""",
                0,
            )
            driver.execute(null, "INSERT INTO dailyCompletionEntity VALUES ('run','2026-10-01',1,2)", 0)
            HabitsDatabase.Schema.migrate(driver, 3, 4)
            fun value(sql: String) = driver.executeQuery(null, sql, { cursor ->
                cursor.next().value
                QueryResult.Value(cursor.getString(0).orEmpty())
            }, 0).value
            assertEquals("run|2026-10-01", value("SELECT id FROM dayPlanEntity"))
            assertEquals("run|2026-10-01", value("SELECT plan_id FROM dailyCompletionEntity"))
            assertEquals("ACTION", value("SELECT kind FROM dailyHabitEntity"))
            assertEquals(
                "0",
                driver.executeQuery(null, "SELECT dated_only FROM dailyHabitEntity", { cursor ->
                    cursor.next().value
                    QueryResult.Value((cursor.getLong(0) ?: -1L).toString())
                }, 0).value,
            )
        } finally {
            driver.close()
        }
    }

    @Test
    fun versionTwoDatabaseAddsSheetLinkSetting() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            driver.execute(null, "CREATE TABLE settingsEntity (key TEXT NOT NULL PRIMARY KEY, value INTEGER NOT NULL)", 0)
            driver.execute(null, "INSERT INTO settingsEntity VALUES ('theme_mode', 2)", 0)
            HabitsDatabase.Schema.migrate(driver, 2, 3)
            val value = driver.executeQuery(null, "SELECT value FROM settingsEntity WHERE key = 'theme_mode'", { cursor ->
                cursor.next().value
                QueryResult.Value(cursor.getLong(0) ?: -1L)
            }, 0).value
            assertEquals(2L, value)
            driver.execute(null, "INSERT INTO textSettingsEntity VALUES ('sheet_url', 'https://docs.google.com/spreadsheets/d/test/edit')", 0)
        } finally {
            driver.close()
        }
    }

    @Test
    fun versionOneDatabaseKeepsHabitsWhenPlansAreAdded() {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        try {
            driver.execute(
                null,
                """CREATE TABLE dailyHabitEntity (
                id TEXT NOT NULL PRIMARY KEY, name TEXT NOT NULL, category_id TEXT,
                monthly_goal INTEGER NOT NULL, display_order INTEGER NOT NULL,
                active INTEGER NOT NULL, created_on TEXT NOT NULL, archived_on TEXT,
                created_at INTEGER NOT NULL, updated_at INTEGER NOT NULL
            )""",
                0,
            )
            driver.execute(null, "INSERT INTO dailyHabitEntity VALUES ('run','Run',NULL,12,0,1,'2026-10-01',NULL,1,1)", 0)
            HabitsDatabase.Schema.migrate(driver, 1, 2)
            val habitCount = driver.executeQuery(null, "SELECT COUNT(*) FROM dailyHabitEntity", { cursor ->
                cursor.next().value
                QueryResult.Value(cursor.getLong(0) ?: 0L)
            }, 0).value
            val planCount = driver.executeQuery(null, "SELECT COUNT(*) FROM weeklyPlanEntity", { cursor ->
                cursor.next().value
                QueryResult.Value(cursor.getLong(0) ?: 0L)
            }, 0).value
            assertEquals(1L, habitCount)
            assertEquals(0L, planCount)
        } finally {
            driver.close()
        }
    }
}
