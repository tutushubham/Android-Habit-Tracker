package com.habitsheet.platform

import androidx.compose.runtime.Composable
import com.habitsheet.presentation.ThemeMode

/**
 * Keeps the system chrome in step with the app's own theme choice: Android status/navigation bar icons, iOS status
 * bar and system sheets. Without it, choosing Dark in the app while the phone is Light leaves unreadable bar icons.
 */
@Composable
expect fun SystemBarsStyle(themeMode: ThemeMode, darkTheme: Boolean)
