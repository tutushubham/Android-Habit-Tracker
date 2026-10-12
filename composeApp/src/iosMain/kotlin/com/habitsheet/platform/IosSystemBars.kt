package com.habitsheet.platform

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.uikit.LocalUIViewController
import com.habitsheet.presentation.ThemeMode
import platform.UIKit.UIUserInterfaceStyle

@Composable
actual fun SystemBarsStyle(themeMode: ThemeMode, darkTheme: Boolean) {
    val controller = LocalUIViewController.current
    DisposableEffect(controller, themeMode) {
        controller.overrideUserInterfaceStyle = when (themeMode) {
            ThemeMode.System -> UIUserInterfaceStyle.UIUserInterfaceStyleUnspecified
            ThemeMode.Light -> UIUserInterfaceStyle.UIUserInterfaceStyleLight
            ThemeMode.Dark -> UIUserInterfaceStyle.UIUserInterfaceStyleDark
        }
        onDispose {}
    }
}
