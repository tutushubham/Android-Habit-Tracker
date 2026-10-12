package com.habitsheet.app

import android.database.sqlite.SQLiteDatabase
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.habitsheet.data.LocalHabitRepository
import com.habitsheet.database.HabitsDatabase
import com.habitsheet.domain.model.DailyHabitCompletion
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.LocalDate
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertTrue

/**
 * Opens a database file that has the schema of each released version (the committed snapshots, packaged as test
 * assets) through the app's real Android driver, so the framework's version check, `onUpgrade` and the migrations run
 * exactly as they do when a person updates the app.
 */
@RunWith(AndroidJUnit4::class)
class DatabaseMigrationOnDeviceTest {
    private val latest = HabitsDatabase.Schema.version.toInt()
    private val databaseFile: File get() = targetContext.getDatabasePath(AndroidDriverFactory.DATABASE_NAME)

    @Before
    fun noDatabase() = deleteAppDatabase()

    @After
    fun cleanUp() = deleteAppDatabase()

    /** Installs the snapshot of [version], adds the rows that version's app would have stored, sets user_version. */
    private fun installDatabaseOf(version: Int) {
        databaseFile.parentFile?.mkdirs()
        // The snapshots are test assets, which live in the instrumentation APK, not in the app's.
        val assets = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().context.assets
        assets.open("$version.db").use { input -> databaseFile.outputStream().use { input.copyTo(it) } }

        val db = SQLiteDatabase.openDatabase(databaseFile.path, null, SQLiteDatabase.OPEN_READWRITE)
        try {
            db.execSQL("INSERT INTO categoryEntity VALUES ('c1', 'Fitness', 0, 1, 100)")
            if (version >= 4) {
                db.execSQL("INSERT INTO dailyHabitEntity (id, name, category_id, monthly_goal, display_order, active, created_on, archived_on, created_at, updated_at, kind, dated_only) VALUES ('run', 'Run', 'c1', 12, 0, 1, '2026-08-01', NULL, 100, 200, 'ACTION', 0)")
                db.execSQL("INSERT INTO dailyCompletionEntity (plan_id, habit_id, date, completed, updated_at) VALUES ('run|2026-08-05', 'run', '2026-08-05', 1, 300)")
            } else {
                db.execSQL("INSERT INTO dailyHabitEntity (id, name, category_id, monthly_goal, display_order, active, created_on, archived_on, created_at, updated_at) VALUES ('run', 'Run', 'c1', 12, 0, 1, '2026-08-01', NULL, 100, 200)")
                db.execSQL("INSERT INTO dailyCompletionEntity (habit_id, date, completed, updated_at) VALUES ('run', '2026-08-05', 1, 300)")
            }
            db.execSQL("INSERT INTO settingsEntity VALUES ('theme_mode', 2)")
            db.version = version
        } finally {
            db.close()
        }
    }

    private fun <T> withRepository(block: suspend (LocalHabitRepository) -> T): T {
        val repository = LocalHabitRepository(AndroidDriverFactory(targetContext))
        try {
            return runBlocking { block(repository) }
        } finally {
            repository.close()
        }
    }

    @Test
    fun everyReleasedVersionUpgradesInPlaceAndKeepsItsData() {
        for (version in 1 until latest) {
            deleteAppDatabase()
            installDatabaseOf(version)

            withRepository { repository ->
                val snapshot = repository.snapshot.value
                assertEquals(listOf("Run"), snapshot.dailyHabits.map { it.name }, "from v$version")
                assertEquals(
                    listOf(LocalDate(2026, 8, 5)),
                    snapshot.dailyCompletions.filter { it.completed }.map { it.date },
                    "from v$version",
                )
                assertEquals(2, repository.getThemeMode(), "from v$version")
                assertEquals("Fitness", snapshot.categories.single { it.id == "c1" }.name, "from v$version")
                // The upgraded database accepts new data.
                repository.setDailyCompletion(DailyHabitCompletion("run", LocalDate(2026, 8, 6), true, 400))
                assertEquals(2, repository.snapshot.value.dailyCompletions.size, "from v$version")
            }

            val raw = SQLiteDatabase.openDatabase(databaseFile.path, null, SQLiteDatabase.OPEN_READONLY)
            try {
                assertEquals(latest, raw.version, "the file is at the current schema version after upgrading from v$version")
            } finally {
                raw.close()
            }
        }
    }

    @Test
    fun theDriverEnforcesForeignKeysSoAnOrphanCheckOffIsRejected() {
        withRepository { repository ->
            // Nothing has this habit id: with foreign keys on, the database refuses the row instead of storing an orphan.
            assertFails {
                repository.setDailyCompletion(DailyHabitCompletion("ghost", LocalDate(2026, 8, 5), true, 1))
            }
            assertTrue(repository.snapshot.value.dailyCompletions.isEmpty())
        }
    }
}
