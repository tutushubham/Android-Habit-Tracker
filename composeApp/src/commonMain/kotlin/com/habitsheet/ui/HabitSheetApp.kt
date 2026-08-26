package com.habitsheet.ui

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.habitsheet.presentation.BackupViewModel
import com.habitsheet.presentation.ManageHabitsViewModel
import com.habitsheet.presentation.MonthViewModel
import com.habitsheet.presentation.SettingsViewModel
import com.habitsheet.presentation.ThemeMode
import com.habitsheet.presentation.VersionProvider

private enum class Destination { Tracker, Manage, Settings, Backup, About }

@Composable
fun HabitSheetApp(
    monthViewModel: MonthViewModel,
    manageHabitsViewModel: ManageHabitsViewModel,
    shareService: ShareService,
    backupViewModel: BackupViewModel,
    settingsViewModel: SettingsViewModel,
    versionProvider: VersionProvider,
) {
    val themeMode by settingsViewModel.themeMode.collectAsState()
    var destination by remember { mutableStateOf(Destination.Tracker) }
    
    val darkTheme = when (themeMode) {
        ThemeMode.System -> androidx.compose.foundation.isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }

    HabitSheetTheme(darkTheme = darkTheme) {
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
                onBackup = { destination = Destination.Backup },
                onAbout = { destination = Destination.About }
            )
            Destination.Backup -> DataBackupScreen(
                viewModel = backupViewModel,
                onBack = { destination = Destination.Settings }
            )
            Destination.About -> AboutScreen(
                versionProvider = versionProvider,
                onBack = { destination = Destination.Settings }
            )
        }
    }
}
