package com.habitsheet.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.habitsheet.presentation.ManageHabitsViewModel
import com.habitsheet.presentation.MonthViewModel

private enum class Destination { Tracker, Manage }

@Composable
fun HabitSheetApp(
    monthViewModel: MonthViewModel,
    manageHabitsViewModel: ManageHabitsViewModel,
) {
    var destination by remember { mutableStateOf(Destination.Tracker) }
    HabitSheetTheme {
        when (destination) {
            Destination.Tracker -> MonthScreen(
                viewModel = monthViewModel,
                onManage = { destination = Destination.Manage },
            )
            Destination.Manage -> ManageHabitsScreen(
                viewModel = manageHabitsViewModel,
                onTracker = { destination = Destination.Tracker },
            )
        }
    }
}
