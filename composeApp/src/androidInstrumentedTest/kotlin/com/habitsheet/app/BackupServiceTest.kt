package com.habitsheet.app

import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.lifecycle.Lifecycle
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.habitsheet.presentation.UiText
import com.habitsheet.ui.BackupResult
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

/**
 * [AndroidBackupService] against the real content resolver and activity-result machinery, with the picker itself
 * replaced: the registry answers every launch at once with a file URI chosen by the test (or null = cancelled).
 */
@RunWith(AndroidJUnit4::class)
class BackupServiceTest {
    /** Answers [onLaunch] with whatever [answer] returns for the contract's input. */
    private class AnsweringRegistry(var answer: (input: Any?) -> Uri?) : ActivityResultRegistry() {
        val launches = mutableListOf<Any?>()

        @Suppress("UNCHECKED_CAST")
        override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>, input: I, options: ActivityOptionsCompat?) {
            launches += input
            dispatchResult(requestCode, answer(input) as O)
        }
    }

    private val registry = AnsweringRegistry { null }
    private val directory = File(targetContext.cacheDir, "backup-service-test")
    private lateinit var scenario: ActivityScenario<ComponentActivity>
    private lateinit var service: AndroidBackupService

    @Before
    fun launchAnActivityThatHasNotStartedYet() {
        directory.deleteRecursively()
        directory.mkdirs()
        scenario = ActivityScenario.launch(ComponentActivity::class.java)
        // Launchers must be registered before the activity is STARTED, exactly as MainActivity does in onCreate.
        scenario.moveToState(Lifecycle.State.CREATED)
        scenario.onActivity { service = AndroidBackupService(it, registry = registry) }
        scenario.moveToState(Lifecycle.State.RESUMED)
    }

    @After
    fun tearDown() {
        scenario.close()
        directory.deleteRecursively()
    }

    private fun onMain(block: () -> Unit) = scenario.onActivity { block() }

    @Test
    fun exportWritesTheTextToTheChosenDocumentAndReportsSuccess() {
        val target = File(directory, "backup.json")
        registry.answer = { Uri.fromFile(target) }
        var result: BackupResult? = null

        onMain { service.exportBackup("""{"version":4}""") { result = it } }

        assertIs<BackupResult.Success>(result)
        assertEquals("""{"version":4}""", target.readText())
        assertTrue(registry.launches.single().toString().startsWith("habitsheet_backup_"), "the suggested file name")
    }

    @Test
    fun csvExportUsesItsOwnPickerAndFile() {
        val target = File(directory, "export.csv")
        registry.answer = { Uri.fromFile(target) }
        var result: BackupResult? = null

        onMain { service.exportCsv("date,habit\n") { result = it } }

        assertIs<BackupResult.Success>(result)
        assertEquals("date,habit\n", target.readText())
        assertTrue(registry.launches.single().toString().startsWith("habitsheet_export_"))
    }

    @Test
    fun cancellingThePickerIsNotAnErrorAndLeavesNothingBehind() {
        registry.answer = { null }
        var result: BackupResult? = null

        onMain { service.exportBackup("{}") { result = it } }

        assertEquals(BackupResult.Cancelled, result)
        val pending = File(targetContext.cacheDir, "pending-export")
        assertTrue(pending.listFiles().orEmpty().isEmpty(), "the staged copy is deleted")
    }

    @Test
    fun importDeliversTheWholeFileText() {
        val source = File(directory, "in.json").apply { writeText("""{"version":4,"data":{}}""") }
        registry.answer = { Uri.fromFile(source) }
        var imported: String? = null

        onMain { service.importBackup(onImport = { imported = it }, onFailure = { error("unexpected: $it") }) }

        assertEquals("""{"version":4,"data":{}}""", imported)
    }

    @Test
    fun anUnreadableFileIsReportedAndNothingIsImported() {
        registry.answer = { Uri.fromFile(File(directory, "missing.json")) }
        var imported: String? = null
        var failure: UiText? = null

        onMain { service.importBackup(onImport = { imported = it }, onFailure = { failure = it }) }

        assertNull(imported)
        assertTrue(failure != null, "the person is told the file could not be read")
    }

    @Test
    fun cancellingTheImportPickerCallsNothing() {
        registry.answer = { null }
        var called = false

        onMain { service.importBackup(onImport = { called = true }, onFailure = { called = true }) }

        assertTrue(!called)
    }
}
