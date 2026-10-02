package com.habitsheet

import com.habitsheet.data.DefaultIdGenerator
import com.habitsheet.data.DriverFactory
import com.habitsheet.data.LocalHabitRepository
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
) {
    private val serializedTokens = SerializingTokenProvider(tokenProvider)
    val repository = LocalHabitRepository(driverFactory)
    val sheetSync = SheetSync(repository, serializedTokens)
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    // "Will sync when online": while offline with check-offs still waiting, retry on its own (30 s, doubling to 5 min).
    private val syncScheduler = SyncScheduler(
        syncScope,
        shouldRetry = { sheetSync.state.value.error is SyncError.Offline && repository.snapshot.value.pendingCompletions.isNotEmpty() },
    ) { interactive -> sheetSync.sync(interactive) }
    val monthViewModel = MonthViewModel(repository, onLocalChange = syncScheduler::markDirty)
    val manageHabitsViewModel = ManageHabitsViewModel(repository, DefaultIdGenerator())
    val settingsViewModel = SettingsViewModel(repository, sheetSync = sheetSync)

    fun syncOnForeground() = syncScheduler.syncNow()

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
