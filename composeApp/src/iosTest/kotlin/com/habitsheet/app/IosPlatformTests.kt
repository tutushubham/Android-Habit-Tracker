@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.habitsheet.app

import app.cash.sqldelight.driver.native.NativeSqliteDriver
import com.habitsheet.data.DatabaseFileNames
import com.habitsheet.data.HabitRepositoryContract
import com.habitsheet.data.LocalHabitRepository
import com.habitsheet.data.RepositoryHandle
import com.habitsheet.database.HabitsDatabase
import com.habitsheet.domain.model.DailyHabitCompletion
import com.habitsheet.domain.repository.HabitRepository
import com.habitsheet.presentation.UiText
import com.habitsheet.resources.Res
import com.habitsheet.resources.recovery_moved_aside
import com.habitsheet.ui.BackupResult
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import platform.Foundation.NSApplicationSupportDirectory
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSLibraryDirectory
import platform.Foundation.NSSearchPathForDirectoriesInDomains
import platform.Foundation.NSTemporaryDirectory
import platform.Foundation.NSUUID
import platform.Foundation.NSUserDomainMask
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFails
import kotlin.test.assertIs
import kotlin.test.assertTrue

/** A real SQLite file under a temporary directory, opened through the same `NativeSqliteDriver` the app uses. */
private class NativeDatabase {
    val directory = NSTemporaryDirectory() + "habitsheet-test-" + NSUUID().UUIDString
    private val opened = mutableListOf<LocalHabitRepository>()

    init {
        NSFileManager.defaultManager.createDirectoryAtPath(directory, true, null, null)
    }

    fun open(): LocalHabitRepository = LocalHabitRepository(
        {
            NativeSqliteDriver(
                schema = HabitsDatabase.Schema,
                name = "test.db",
                onConfiguration = { config -> config.copy(extendedConfig = config.extendedConfig.copy(basePath = directory)) },
            )
        },
    ).also { opened += it }

    fun cleanUp() {
        opened.forEach { runCatching { it.close() } }
        NSFileManager.defaultManager.removeItemAtPath(directory, null)
    }
}

/** The whole repository contract on the native driver [mac]: every store method, upserts, cascades, restart. */
class IosRepositoryContractTest : HabitRepositoryContract() {
    override suspend fun withRepository(block: suspend (RepositoryHandle) -> Unit) {
        val database = NativeDatabase()
        try {
            block(
                object : RepositoryHandle {
                    private var current = database.open()
                    override val repository: HabitRepository get() = current

                    override suspend fun reopen(): HabitRepository {
                        current.close()
                        current = database.open()
                        return current
                    }
                },
            )
        } finally {
            database.cleanUp()
        }
    }
}

class IosDriverTest {
    @Test
    fun theNativeDriverEnforcesForeignKeysSoAnOrphanCheckOffIsRejected() = runTest {
        val database = NativeDatabase()
        try {
            val repository = database.open()
            assertFails { repository.setDailyCompletion(DailyHabitCompletion("ghost", LocalDate(2026, 8, 5), true, 1)) }
            assertTrue(repository.snapshot.value.dailyCompletions.isEmpty())
        } finally {
            database.cleanUp()
        }
    }
}

/**
 * Where does `NativeSqliteDriver` put the app's database, and does the recovery code find and move it? (The
 * recovery code searches the usual directories because this was never verified.) Uses the default location of the
 * test process's own container, never the app's.
 */
class IosStartupRecoveryTest {
    private val manager = NSFileManager.defaultManager
    private val name = "habit-sheet.db"

    private fun directories(): List<String> = listOf(NSDocumentDirectory, NSApplicationSupportDirectory, NSLibraryDirectory)
        .mapNotNull { NSSearchPathForDirectoriesInDomains(it, NSUserDomainMask, true).firstOrNull() as? String }
        .flatMap { listOf(it, "$it/databases") }

    private fun filesNamed(prefix: String): List<String> = directories().flatMap { directory ->
        manager.contentsOfDirectoryAtPath(directory, null).orEmpty().filterIsInstance<String>()
            .filter { it.startsWith(prefix) }.map { "$directory/$it" }
    }

    @Test
    fun theDatabaseIsFoundWhereTheDriverPutItAndMovedAside() {
        IosDriverFactory().createDriver().close()
        assertTrue(filesNamed(name).any { it.endsWith("/$name") }, "the driver created $name in one of the searched directories")

        val result = IosStartupRecovery({ null }).setAsideDatabase()

        try {
            assertEquals(BackupResult.Success(UiText.of(Res.string.recovery_moved_aside)), result)
            assertTrue(filesNamed(name).none { it.endsWith("/$name") }, "the original file is gone")
            assertTrue(filesNamed(name).any { ".damaged-" in it }, "a copy is kept")
        } finally {
            filesNamed(name).filter { ".damaged-" in it || DatabaseFileNames.filesOf(name).any { f -> it.endsWith("/$f") } }
                .forEach { manager.removeItemAtPath(it, null) }
        }
        assertIs<BackupResult.Success>(result)
    }
}
