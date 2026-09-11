package com.habitsheet.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.habitsheet.presentation.SettingsViewModel
import com.habitsheet.presentation.ThemeMode
import com.habitsheet.presentation.ManageHabitsViewModel
import com.habitsheet.presentation.MonthViewModel

private enum class Destination { Tracker, Manage, Settings }

@Composable
fun HabitSheetApp(
    monthViewModel: MonthViewModel,
    manageHabitsViewModel: ManageHabitsViewModel,
    shareService: ShareService,
    settingsViewModel: SettingsViewModel,
) {
    var destination by remember { mutableStateOf(Destination.Tracker) }

    val themeMode by settingsViewModel.themeMode.collectAsState()
    HabitSheetTheme(darkTheme = when (themeMode) {
        ThemeMode.System -> androidx.compose.foundation.isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }) {
        when (destination) {
            Destination.Tracker -> MonthScreen(
                viewModel = monthViewModel,
                onManage = { destination = Destination.Manage },
                onSettings = { destination = Destination.Settings },
                shareService = shareService,
            )
            Destination.Manage -> ManageHabitsScreen(
                viewModel = manageHabitsViewModel,
                onTracker = { destination = Destination.Tracker },
            )
            Destination.Settings -> SettingsScreen(
                viewModel = settingsViewModel,
                onBack = { destination = Destination.Tracker },
                onManageCategories = { destination = Destination.Manage },
                onBackup = {},
                onAbout = {},
            )
        }
    }
}
