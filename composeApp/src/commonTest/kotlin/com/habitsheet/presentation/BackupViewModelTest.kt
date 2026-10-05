package com.habitsheet.presentation

import com.habitsheet.data.InMemoryHabitRepository
import com.habitsheet.domain.backup.BackupSerializer
import com.habitsheet.domain.model.Category
import com.habitsheet.domain.model.DailyHabit
import com.habitsheet.domain.model.DailyHabitCompletion
import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.ui.BackupResult
import com.habitsheet.ui.BackupService
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class BackupViewModelTest {
    private val dateProvider = object : DateProvider {
        override fun today() = LocalDate(2026, 8, 26)
        override fun nowEpochMillis() = 4321L
    }

    @Test
    fun exportsRestorableJsonAndCsv() = runTest {
        val snapshot = validSnapshot()
        val service = RecordingBackupService()
        val viewModel = BackupViewModel(
            repository = InMemoryHabitRepository(snapshot),
            backupService = service,
            dateProvider = dateProvider,
            scope = backgroundScope,
            callbackDispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        viewModel.exportBackup()
        viewModel.exportCsv()

        val json = assertNotNull(service.exportedBackup)
        assertEquals(snapshot, BackupSerializer.deserialize(json))
        assertTrue(json.contains("\"timestamp\": 4321"))
        assertTrue(assertNotNull(service.exportedCsv).contains("Read"))
    }

    @Test
    fun exportIncludesThemeAndOnboardingAndTheSheetLinkOnlyOnRequest() = runTest {
        val repository = InMemoryHabitRepository(validSnapshot())
        repository.setThemeMode(2)
        repository.setOnboardingCompleted(true)
        repository.setSheetUrl("https://docs.google.com/spreadsheets/d/mine/edit")
        val service = RecordingBackupService()
        val viewModel = BackupViewModel(
            repository = repository,
            backupService = service,
            dateProvider = dateProvider,
            scope = backgroundScope,
            callbackDispatcher = UnconfinedTestDispatcher(testScheduler),
        )

        viewModel.exportBackup()
        val plain = BackupSerializer.parse(assertNotNull(service.exportedBackup))
        assertEquals(com.habitsheet.domain.backup.BackupSettings(themeMode = 2, onboardingCompleted = true), plain.settings)
        assertFalse("docs.google.com" in service.exportedBackup.orEmpty())

        viewModel.exportBackup(includeSheetLink = true)
        val withLink = BackupSerializer.parse(assertNotNull(service.exportedBackup))
        assertEquals("https://docs.google.com/spreadsheets/d/mine/edit", withLink.settings?.sheetUrl)
    }

    @Test
    fun importOfV4BackupResetsSyncStateAndAppliesTheLinkOnlyWhenTheCallerSaysSo() = runTest {
        val payload = BackupSerializer.serialize(
            validSnapshot(),
            4321,
            com.habitsheet.domain.backup.BackupSettings(themeMode = 1, sheetUrl = "https://docs.google.com/spreadsheets/d/backup/edit"),
        )
        for (apply in listOf(false, true)) {
            val repository = InMemoryHabitRepository()
            repository.setSheetUrl("https://docs.google.com/spreadsheets/d/current/edit")
            repository.setSheetSyncedKeys(setOf("a"))
            repository.setSheetLastSync(55)
            val viewModel = BackupViewModel(
                repository = repository,
                backupService = RecordingBackupService(importPayload = payload),
                scope = backgroundScope,
                callbackDispatcher = UnconfinedTestDispatcher(testScheduler),
            )
            var succeeded = false

            viewModel.importBackup(onSuccess = { succeeded = true }, onError = { error("unexpected: $it") }, applySheetLink = apply)
            runCurrent()

            assertTrue(succeeded)
            assertEquals(1, repository.getThemeMode())
            assertEquals(emptySet(), repository.getSheetSyncedKeys())
            assertEquals(0L, repository.getSheetLastSync())
            assertEquals(
                if (apply) "https://docs.google.com/spreadsheets/d/backup/edit" else "https://docs.google.com/spreadsheets/d/current/edit",
                repository.getSheetUrl(),
            )
        }
    }

    @Test
    fun exportReportsTheServicesResultToTheCaller() = runTest {
        for (result in listOf(BackupResult.Success("ok"), BackupResult.Failure("disk full"), BackupResult.Cancelled)) {
            val viewModel = BackupViewModel(
                repository = InMemoryHabitRepository(validSnapshot()),
                backupService = RecordingBackupService(exportResult = result),
                scope = backgroundScope,
                callbackDispatcher = UnconfinedTestDispatcher(testScheduler),
            )
            val seen = mutableListOf<BackupResult>()
            viewModel.exportBackup(onResult = { seen += it })
            viewModel.exportCsv { seen += it }
            assertEquals(listOf(result, result), seen)
        }
    }

    @Test
    fun unreadableFileOrEmptyClipboardIsReportedAndNothingIsWiped() = runTest {
        val original = HabitSnapshot(categories = listOf(Category("old", "Old", 0, true, 1)))
        val repository = InMemoryHabitRepository(original)
        val viewModel = BackupViewModel(
            repository = repository,
            backupService = RecordingBackupService(importFailure = "The clipboard is empty."),
            scope = backgroundScope,
            callbackDispatcher = UnconfinedTestDispatcher(testScheduler),
        )
        var succeeded = false
        var error: String? = null

        viewModel.importBackup(onSuccess = { succeeded = true }, onError = { error = it })
        runCurrent()

        assertFalse(succeeded)
        assertEquals("The clipboard is empty.", error)
        assertEquals(original, repository.snapshot.value)
    }

    @Test
    fun validImportReplacesDataAndCallsSuccessOnce() = runTest {
        val replacement = validSnapshot()
        val service = RecordingBackupService(
            importPayload = BackupSerializer.serialize(replacement, 4321),
        )
        val repository = InMemoryHabitRepository(
            HabitSnapshot(categories = listOf(Category("old", "Old", 0, true, 1))),
        )
        val viewModel = BackupViewModel(
            repository = repository,
            backupService = service,
            scope = backgroundScope,
            callbackDispatcher = UnconfinedTestDispatcher(testScheduler),
        )
        var successCount = 0
        var error: String? = null

        viewModel.importBackup(
            onSuccess = { successCount += 1 },
            onError = { error = it },
        )
        runCurrent()

        assertEquals(replacement, repository.snapshot.value)
        assertEquals(1, successCount)
        assertNull(error)
    }

    @Test
    fun invalidImportKeepsExistingDataAndReturnsFriendlyError() = runTest {
        val original = HabitSnapshot(categories = listOf(Category("old", "Old", 0, true, 1)))
        val service = RecordingBackupService(importPayload = "not json")
        val repository = InMemoryHabitRepository(original)
        val viewModel = BackupViewModel(
            repository = repository,
            backupService = service,
            scope = backgroundScope,
            callbackDispatcher = UnconfinedTestDispatcher(testScheduler),
        )
        var succeeded = false
        var error: String? = null

        viewModel.importBackup(
            onSuccess = { succeeded = true },
            onError = { error = it },
        )
        runCurrent()

        assertFalse(succeeded)
        assertEquals(original, repository.snapshot.value)
        assertEquals("This isn't a valid Habit Sheet backup file.", error)
    }

    @Test
    fun inconsistentImportExplainsProblemWithoutReplacingData() = runTest {
        val original = HabitSnapshot(categories = listOf(Category("old", "Old", 0, true, 1)))
        val invalid = HabitSnapshot(
            dailyCompletions = listOf(
                DailyHabitCompletion("missing", LocalDate(2026, 8, 2), true, 2),
            ),
        )
        val service = RecordingBackupService(
            importPayload = BackupSerializer.serialize(invalid, 4321),
        )
        val repository = InMemoryHabitRepository(original)
        val viewModel = BackupViewModel(
            repository = repository,
            backupService = service,
            scope = backgroundScope,
            callbackDispatcher = UnconfinedTestDispatcher(testScheduler),
        )
        var error: String? = null

        viewModel.importBackup(onSuccess = {}, onError = { error = it })
        runCurrent()

        assertEquals(original, repository.snapshot.value)
        assertTrue(error.orEmpty().contains("missing habit"))
    }

    private fun validSnapshot(): HabitSnapshot {
        val category = Category("cat", "Learning", 0, true, 1000)
        val habit = DailyHabit(
            id = "read",
            name = "Read",
            categoryId = category.id,
            monthlyGoal = 20,
            displayOrder = 0,
            active = true,
            createdOn = LocalDate(2026, 8, 1),
            createdAtEpochMillis = 1000,
            updatedAtEpochMillis = 1000,
        )
        return HabitSnapshot(
            categories = listOf(category),
            dailyHabits = listOf(habit),
            dailyCompletions = listOf(
                DailyHabitCompletion(habit.id, LocalDate(2026, 8, 2), true, 2000),
            ),
        )
    }
}

private class RecordingBackupService(
    private val importPayload: String? = null,
    private val importFailure: String? = null,
    private val exportResult: BackupResult = BackupResult.Success("saved"),
) : BackupService {
    var exportedBackup: String? = null
    var exportedCsv: String? = null

    override fun exportBackup(json: String, onResult: (BackupResult) -> Unit) {
        exportedBackup = json
        onResult(exportResult)
    }

    override fun exportCsv(csv: String, onResult: (BackupResult) -> Unit) {
        exportedCsv = csv
        onResult(exportResult)
    }

    override fun importBackup(onImport: (String) -> Unit, onFailure: (String) -> Unit) {
        importFailure?.let(onFailure)
        importPayload?.let(onImport)
    }
}
