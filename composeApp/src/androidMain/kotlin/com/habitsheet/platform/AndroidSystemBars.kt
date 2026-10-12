package com.habitsheet.platform

import android.content.Context
import android.content.ContextWrapper
import android.graphics.Color
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.platform.LocalContext
import com.habitsheet.presentation.ThemeMode

private const val SCRIM_ALPHA = 0x80
private const val SCRIM_GREY = 0x1b

@Composable
actual fun SystemBarsStyle(themeMode: ThemeMode, darkTheme: Boolean) {
    val activity = LocalContext.current.findActivity() ?: return
    DisposableEffect(activity, darkTheme) {
        val style = if (darkTheme) {
            SystemBarStyle.dark(Color.TRANSPARENT)
        } else {
            SystemBarStyle.light(Color.TRANSPARENT, Color.argb(SCRIM_ALPHA, SCRIM_GREY, SCRIM_GREY, SCRIM_GREY))
        }
        activity.enableEdgeToEdge(statusBarStyle = style, navigationBarStyle = style)
        onDispose {}
    }
}

private fun Context.findActivity(): ComponentActivity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is ComponentActivity) return current
        current = current.baseContext
    }
    return null
}
