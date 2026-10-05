package com.habitsheet.data

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.habitsheet.database.HabitsDatabase
import kotlin.test.Test
import kotlin.test.assertEquals

/** Schema 4 -> 5: `dailyCompletionEntity.pending_upload`, backfilled with the old `updated_at > lastSync` rule. */
class PendingUploadMigrationTest {
    private fun driverAtVersion4(lastSync: String?): JdbcSqliteDriver {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        driver.execute(
            null,
            """CREATE TABLE dailyCompletionEntity (
            plan_id TEXT NOT NULL, habit_id TEXT NOT NULL, date TEXT NOT NULL,
            completed INTEGER NOT NULL, updated_at INTEGER NOT NULL, PRIMARY KEY (plan_id, date)
        )""",
            0,
        )
        driver.execute(null, "CREATE TABLE textSettingsEntity (key TEXT NOT NULL PRIMARY KEY, value TEXT NOT NULL)", 0)
        driver.execute(null, "INSERT INTO dailyCompletionEntity VALUES ('a','run','2026-10-01',1,50)", 0)
        driver.execute(null, "INSERT INTO dailyCompletionEntity VALUES ('b','run','2026-10-02',1,100)", 0)
        driver.execute(null, "INSERT INTO dailyCompletionEntity VALUES ('c','run','2026-10-03',0,150)", 0)
        if (lastSync != null) driver.execute(null, "INSERT INTO textSettingsEntity VALUES ('sheet_last_sync', '$lastSync')", 0)
        return driver
    }

    private fun flags(driver: JdbcSqliteDriver): Map<String, Long> = driver.executeQuery(null, "SELECT plan_id, pending_upload FROM dailyCompletionEntity ORDER BY plan_id", { cursor ->
        val result = linkedMapOf<String, Long>()
        while (cursor.next().value) result[cursor.getString(0)!!] = cursor.getLong(1)!!
        QueryResult.Value(result)
    }, 0).value

    @Test
    fun completionsChangedAfterLastSyncBecomePendingOthersDoNot() {
        val driver = driverAtVersion4(lastSync = "100")
        try {
            HabitsDatabase.Schema.migrate(driver, 4, 5)
            assertEquals(mapOf("a" to 0L, "b" to 0L, "c" to 1L), flags(driver))
        } finally {
            driver.close()
        }
    }

    @Test
    fun neverSyncedDatabaseHasNothingPending() {
        for (lastSync in listOf(null, "0", "")) {
            val driver = driverAtVersion4(lastSync)
            try {
                HabitsDatabase.Schema.migrate(driver, 4, 5)
                assertEquals(mapOf("a" to 0L, "b" to 0L, "c" to 0L), flags(driver), "lastSync=$lastSync")
            } finally {
                driver.close()
            }
        }
    }

    @Test
    fun migrationKeepsEveryRowAndValueIntact() {
        val driver = driverAtVersion4("100")
        try {
            HabitsDatabase.Schema.migrate(driver, 4, 5)
            val rows = driver.executeQuery(null, "SELECT plan_id, habit_id, date, completed, updated_at FROM dailyCompletionEntity ORDER BY plan_id", { cursor ->
                val list = mutableListOf<String>()
                while (cursor.next().value) list += (0..4).joinToString("|") { cursor.getString(it) ?: cursor.getLong(it).toString() }
                QueryResult.Value(list)
            }, 0).value
            assertEquals(listOf("a|run|2026-10-01|1|50", "b|run|2026-10-02|1|100", "c|run|2026-10-03|0|150"), rows)
        } finally {
            driver.close()
        }
    }
}
