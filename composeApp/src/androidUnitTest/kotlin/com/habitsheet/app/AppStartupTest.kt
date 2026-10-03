package com.habitsheet.app

import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.habitsheet.AppStartup
import com.habitsheet.StartupState
import com.habitsheet.data.DriverFactory
import com.habitsheet.database.HabitsDatabase
import com.habitsheet.platform.LogLevel
import com.habitsheet.platform.RecordingLogger
import com.habitsheet.sync.SheetTokenProvider
import java.io.File
import java.nio.file.Files
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** What happens when the local database cannot be opened: the app must not crash and must not lose the file. */
class AppStartupTest {
    private val directory: File = Files.createTempDirectory("habit-sheet-startup").toFile()
    private val dbFile = File(directory, "habit-sheet.db")
    private val tokens = object : SheetTokenProvider {
        override fun requestToken(interactive: Boolean, completion: (String?, String?) -> Unit) = completion(null, null)
    }

    @AfterTest
    fun cleanUp() {
        directory.deleteRecursively()
    }

    /** Like the real drivers: creates the schema for a missing file, otherwise opens what is there. */
    private fun fileFactory(migrateFrom: Long? = null) = DriverFactory {
        val existed = dbFile.exists()
        val driver: SqlDriver = JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}")
        try {
            if (!existed) HabitsDatabase.Schema.create(driver)
            else if (migrateFrom != null) HabitsDatabase.Schema.migrate(driver, migrateFrom, HabitsDatabase.Schema.version)
        } catch (e: Throwable) {
            driver.close()
            throw e
        }
        driver
    }

    private val garbage = ByteArray(4096) { (it * 31).toByte() }

    @Test
    fun aHealthyDatabaseStartsTheApp() {
        val state = AppStartup.create(fileFactory(), tokens)
        assertIs<StartupState.Ready>(state).graph.close()
    }

    @Test
    fun aDriverThatThrowsGivesTheRecoveryStateInsteadOfCrashing() {
        val logger = RecordingLogger()
        val state = AppStartup.create({ throw IllegalStateException("cannot open") }, tokens, logger)

        val failed = assertIs<StartupState.Failed>(state)
        assertIs<IllegalStateException>(failed.cause)
        assertTrue(logger.entries.any { it.level == LogLevel.ERROR && it.tag == "Startup" })
    }

    @Test
    fun aCorruptFileGivesTheRecoveryStateLeavesTheBytesUntouchedAndReleasesTheFile() {
        dbFile.writeBytes(garbage)

        val state = AppStartup.create(fileFactory(), tokens)

        assertIs<StartupState.Failed>(state)
        assertContentEquals(garbage, dbFile.readBytes(), "a failed start must not modify the file")
        // (The JDBC driver does not hold the file open between statements, so this cannot prove that the real
        // Android/iOS drivers release it; LocalHabitRepository closes its driver on a failed open for that reason.)
        assertTrue(dbFile.renameTo(File(directory, "moved")), "the file must be free to move")
    }

    @Test
    fun aMigrationThatFailsGivesTheRecoveryStateInsteadOfCrashing() {
        // A version 1 database that already contains a table 1.sqm wants to create: the migration throws.
        java.nio.file.Files.copy(
            File("src/commonMain/sqldelight/databases/1.db").takeIf { it.exists() }?.toPath()
                ?: File("composeApp/src/commonMain/sqldelight/databases/1.db").toPath(),
            dbFile.toPath(),
        )
        JdbcSqliteDriver("jdbc:sqlite:${dbFile.absolutePath}").also {
            it.execute(null, "CREATE TABLE weeklyPlanEntity (surprise TEXT)", 0)
            it.close()
        }

        val state = AppStartup.create(fileFactory(migrateFrom = 1), tokens)

        assertIs<StartupState.Failed>(state)
        assertTrue(dbFile.length() > 0)
        assertTrue(dbFile.renameTo(File(directory, "moved")), "the file must be free to move")
    }

    @Test
    fun movingTheCorruptFileAsideAndRetryingStartsEmptyWhileKeepingTheOldBytes() {
        dbFile.writeBytes(garbage)
        val files = DatabaseFiles(directory, "habit-sheet.db")
        assertIs<StartupState.Failed>(AppStartup.create(fileFactory(), tokens))

        files.setAside(123)
        val retried = AppStartup.create(fileFactory(), tokens)

        val graph = assertIs<StartupState.Ready>(retried).graph
        assertTrue(graph.repository.snapshot.value.dailyHabits.isEmpty())
        graph.close()
        assertContentEquals(garbage, File(directory, "habit-sheet.db.damaged-123").readBytes())
        assertFalse(files.existing().isEmpty(), "a new empty database was created")
    }

    @Test
    fun retryingWithoutChangingAnythingKeepsFailingAndStillChangesNothing() {
        dbFile.writeBytes(garbage)
        repeat(3) { assertIs<StartupState.Failed>(AppStartup.create(fileFactory(), tokens)) }
        assertContentEquals(garbage, dbFile.readBytes())
        assertEquals(listOf("habit-sheet.db"), directory.list()!!.toList())
    }
}
