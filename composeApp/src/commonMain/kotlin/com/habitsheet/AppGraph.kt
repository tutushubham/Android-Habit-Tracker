package com.habitsheet

import com.habitsheet.data.DefaultIdGenerator
import com.habitsheet.data.DriverFactory
import com.habitsheet.data.LocalHabitRepository
import com.habitsheet.platform.Logger
import com.habitsheet.platform.NoOpLogger
import com.habitsheet.presentation.ManageHabitsViewModel
import com.habitsheet.presentation.MonthViewModel
import com.habitsheet.presentation.SettingsViewModel
import com.habitsheet.sync.SerializingTokenProvider
import com.habitsheet.sync.SheetSync
import com.habitsheet.sync.SheetTokenProvider
import com.habitsheet.sync.SyncError
import com.habitsheet.sync.SyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel

class AppGraph(
    driverFactory: DriverFactory,
    tokenProvider: SheetTokenProvider,
    val logger: Logger = NoOpLogger,
) {
    private val serializedTokens = SerializingTokenProvider(tokenProvider, logger = logger)
    val repository = LocalHabitRepository(driverFactory)
    val sheetSync = SheetSync(repository, serializedTokens, logger = logger)
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    // "Will sync when online": while offline with check-offs still waiting, retry on its own (30 s, doubling to 5 min).
    private val syncScheduler = SyncScheduler(
        syncScope,
        logger = logger,
        shouldRetry = { sheetSync.state.value.error is SyncError.Offline && repository.snapshot.value.pendingCompletions.isNotEmpty() },
    ) { interactive -> sheetSync.sync(interactive) }
    val monthViewModel = MonthViewModel(repository, onLocalChange = syncScheduler::markDirty, logger = logger)
    val manageHabitsViewModel = ManageHabitsViewModel(repository, DefaultIdGenerator(), logger = logger)
    val settingsViewModel = SettingsViewModel(repository, sheetSync = sheetSync, logger = logger)

    fun syncOnForeground() = syncScheduler.syncNow()

    /** The app came to the foreground, or the system date / time zone changed: re-evaluate "today". */
    fun refreshToday() = monthViewModel.refreshToday()

    fun close() {
        monthViewModel.close()
        manageHabitsViewModel.close()
        settingsViewModel.close()
        syncScope.cancel()
        sheetSync.close()
        serializedTokens.close()
        repository.close()
    }
}
