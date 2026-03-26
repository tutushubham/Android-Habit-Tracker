package com.tutushubham.pokidex.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

@Immutable
data class SemanticColorScheme(
    val deficit: Color,
    val momentum: Color,
    val warning: Color,
    val neutral: Color,
    val info: Color
)

val LightSemanticColors = SemanticColorScheme(
    deficit = Color(0xFFD32F2F),
    momentum = Color(0xFF2E7D32),
    warning = Color(0xFFE65100),
    neutral = Color(0xFF616161),
    info = Color(0xFF4361EE)
)

val DarkSemanticColors = SemanticColorScheme(
    deficit = Color(0xFFEF5350),
    momentum = Color(0xFF66BB6A),
    warning = Color(0xFFFFB74D),
    neutral = Color(0xFFBDBDBD),
    info = Color(0xFF6B8AFF)
)

val LocalSemanticColors = staticCompositionLocalOf { LightSemanticColors }

object AppSemanticColors {
    val deficit @Composable get() = LocalSemanticColors.current.deficit
    val momentum @Composable get() = LocalSemanticColors.current.momentum
    val warning @Composable get() = LocalSemanticColors.current.warning
    val neutral @Composable get() = LocalSemanticColors.current.neutral
    val info @Composable get() = LocalSemanticColors.current.info
}
