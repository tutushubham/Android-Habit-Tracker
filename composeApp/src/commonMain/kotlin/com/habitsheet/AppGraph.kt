package com.habitsheet

import com.habitsheet.data.DefaultIdGenerator
import com.habitsheet.data.DriverFactory
import com.habitsheet.data.LocalHabitRepository
import com.habitsheet.presentation.ManageHabitsViewModel
import com.habitsheet.presentation.MonthViewModel
import com.habitsheet.presentation.SettingsViewModel

class AppGraph(
    driverFactory: DriverFactory,
) {
    val repository = LocalHabitRepository(driverFactory)
    val monthViewModel = MonthViewModel(repository)
    val manageHabitsViewModel = ManageHabitsViewModel(repository, DefaultIdGenerator())
    val settingsViewModel = SettingsViewModel(repository)

    fun close() {
        monthViewModel.close()
        manageHabitsViewModel.close()
        settingsViewModel.close()
        repository.close()
    }
}
