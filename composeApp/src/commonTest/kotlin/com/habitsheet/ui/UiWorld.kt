package com.habitsheet.ui

import com.habitsheet.data.DefaultIdGenerator
import com.habitsheet.data.InMemoryHabitRepository
import com.habitsheet.domain.model.HabitSnapshot
import com.habitsheet.presentation.BackupViewModel
import com.habitsheet.presentation.ManageHabitsViewModel
import com.habitsheet.presentation.MonthViewModel
import com.habitsheet.presentation.SettingsViewModel
import com.habitsheet.presentation.UiText
import com.habitsheet.testing.FakeDateProvider
import com.habitsheet.testing.buildSnapshot
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.datetime.LocalDate

/** A backup service for UI tests: records exports and hands out a prepared file on import. */
internal class FakeBackupService(var fileToImport: String? = null) : BackupService {
    val exportedBackups = mutableListOf<String>()
    val exportedCsv = mutableListOf<String>()

    override fun exportBackup(json: String, onResult: (BackupResult) -> Unit) {
        exportedBackups += json
        onResult(BackupResult.Success(UiText.Raw("Backup saved.")))
    }

    override fun exportCsv(csv: String, onResult: (BackupResult) -> Unit) {
        exportedCsv += csv
        onResult(BackupResult.Success(UiText.Raw("CSV saved.")))
    }

    override fun importBackup(onImport: (String) -> Unit, onFailure: (UiText) -> Unit) {
        val file = fileToImport
        if (file != null) onImport(file) else onFailure(UiText.Raw("No file chosen."))
    }
}

/**
 * Everything a screen test needs: a repository, a controllable clock and real ViewModels running on an unconfined
 * scope, so state changes are visible to the UI as soon as they are made. Call [close] at the end of a scenario.
 *
 * The date is Monday 12 October 2026.
 */
internal class UiWorld(
    initial: HabitSnapshot = HabitSnapshot(),
    onboardingCompleted: Boolean = true,
    today: LocalDate = LocalDate(2026, 10, 12),
    val backupService: FakeBackupService = FakeBackupService(),
) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    val dates = FakeDateProvider(today = today, nowMillis = 1_000L, tickMillis = 1)
    val repository = InMemoryHabitRepository(initial)

    init {
        // Unconfined: the launch body runs to completion right here (nothing in it suspends for real).
        scope.launch { repository.setOnboardingCompleted(onboardingCompleted) }
    }

    val month by lazy { MonthViewModel(repository, repository, dates, scope) }
    val manage by lazy { ManageHabitsViewModel(repository, DefaultIdGenerator(), dates, scope) }
    val settings by lazy { SettingsViewModel(repository, scope, Dispatchers.Unconfined) }
    val backup by lazy { BackupViewModel(repository, backupService, dates, scope, Dispatchers.Unconfined) }

    /** Runs repository calls that suspend only formally. */
    fun now(block: suspend () -> Unit) {
        scope.launch { block() }
    }

    fun close() = scope.cancel()

    companion object {
        /** A habit with no schedule (every day), two sessions of a dated habit today, and a weekly habit. */
        fun sampleData(): HabitSnapshot = buildSnapshot(stamp = 1) {
            category("study", "Study")
            dailyHabit("read", "Read", category = "study", goal = 20, createdOn = LocalDate(2026, 9, 1))
            dailyHabit("run", "Run", goal = 12, createdOn = LocalDate(2026, 9, 1), datedOnly = true)
            dayPlan("run", LocalDate(2026, 10, 12), "Long run", id = "run-am")
            dayPlan("run", LocalDate(2026, 10, 12), "Strides", id = "run-pm")
            weeklyHabit("call", "Call parents", createdOn = LocalDate(2026, 9, 1))
        }
    }
}
