package com.habitsheet

import com.habitsheet.data.DefaultIdGenerator
import com.habitsheet.data.DriverFactory
import com.habitsheet.data.LocalHabitRepository
import com.habitsheet.presentation.ManageHabitsViewModel
import com.habitsheet.presentation.MonthViewModel
import com.habitsheet.presentation.SettingsViewModel
import com.habitsheet.sync.SheetSync
import com.habitsheet.sync.SheetTokenProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

class AppGraph(
    driverFactory: DriverFactory,
    tokenProvider: SheetTokenProvider,
) {
    val repository = LocalHabitRepository(driverFactory)
    val sheetSync = SheetSync(repository, tokenProvider)
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    val monthViewModel = MonthViewModel(repository, onLocalChange = { syncScope.launch { sheetSync.sync() } })
    val manageHabitsViewModel = ManageHabitsViewModel(repository, DefaultIdGenerator())
    val settingsViewModel = SettingsViewModel(repository, sheetSync = sheetSync)

    fun syncOnForeground() { syncScope.launch { sheetSync.sync() } }

    fun close() {
        monthViewModel.close()
        manageHabitsViewModel.close()
        settingsViewModel.close()
        syncScope.cancel()
        sheetSync.close()
        repository.close()
    }
}
