package com.habitsheet

import com.habitsheet.data.DefaultIdGenerator
import com.habitsheet.data.DriverFactory
import com.habitsheet.data.LocalHabitRepository
import com.habitsheet.presentation.ManageHabitsViewModel
import com.habitsheet.presentation.MonthViewModel

class AppGraph(driverFactory: DriverFactory) {
    val repository = LocalHabitRepository(driverFactory)
    val monthViewModel = MonthViewModel(repository)
    val manageHabitsViewModel = ManageHabitsViewModel(repository, DefaultIdGenerator())

    fun close() {
        monthViewModel.close()
        manageHabitsViewModel.close()
        repository.close()
    }
}
