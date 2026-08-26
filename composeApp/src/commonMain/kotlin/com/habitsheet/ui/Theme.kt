package com.habitsheet.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.material3.Typography
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.habitsheet.resources.Res
import com.habitsheet.resources.hanken_grotesk
import com.habitsheet.resources.inter
import com.habitsheet.resources.jetbrains_mono
import org.jetbrains.compose.resources.Font

val HabitGreen = Color(0xFF166534)
val HabitGreenDark = Color(0xFF8BD79B)
val HabitSlate = Color(0xFF475569)
val HabitTertiary = Color(0xFF71717A)

private val LightColors = lightColorScheme(
    primary = Color(0xFF004C22),
    onPrimary = Color.White,
    primaryContainer = HabitGreen,
    onPrimaryContainer = Color(0xFF93E0A2),
    secondary = Color(0xFF515F74),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFD5E3FC),
    onSecondaryContainer = Color(0xFF3A485B),
    tertiary = Color(0xFF3F4048),
    background = Color(0xFFF7F9FB),
    onBackground = Color(0xFF191C1E),
    surface = Color(0xFFF7F9FB),
    onSurface = Color(0xFF191C1E),
    surfaceVariant = Color(0xFFECEEF0),
    onSurfaceVariant = Color(0xFF404940),
    outline = Color(0xFF707A6F),
    outlineVariant = Color(0xFFBFC9BD),
    error = Color(0xFFBA1A1A),
)

private val DarkColors = darkColorScheme(
    primary = HabitGreenDark,
    onPrimary = Color(0xFF003918),
    primaryContainer = Color(0xFF0F5A2B),
    onPrimaryContainer = Color(0xFFA6F4B5),
    secondary = Color(0xFFB9C7DF),
    onSecondary = Color(0xFF233247),
    secondaryContainer = Color(0xFF3A485B),
    onSecondaryContainer = Color(0xFFD5E3FC),
    tertiary = Color(0xFFC6C5CF),
    background = Color(0xFF101411),
    onBackground = Color(0xFFE5E9E5),
    surface = Color(0xFF101411),
    onSurface = Color(0xFFE5E9E5),
    surfaceVariant = Color(0xFF242A26),
    onSurfaceVariant = Color(0xFFBEC9BF),
    outline = Color(0xFF899389),
    outlineVariant = Color(0xFF3F4941),
    error = Color(0xFFFFB4AB),
)

@Composable
private fun habitTypography(): Typography {
    val headline = FontFamily(Font(Res.font.hanken_grotesk, FontWeight.SemiBold))
    val body = FontFamily(Font(Res.font.inter, FontWeight.Normal))
    val mono = FontFamily(Font(Res.font.jetbrains_mono, FontWeight.Medium))
    return Typography(
        headlineLarge = TextStyle(fontFamily = headline, fontWeight = FontWeight.SemiBold, fontSize = 30.sp, lineHeight = 36.sp),
        headlineMedium = TextStyle(fontFamily = headline, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
        titleLarge = TextStyle(fontFamily = headline, fontWeight = FontWeight.SemiBold, fontSize = 20.sp, lineHeight = 28.sp),
        titleMedium = TextStyle(fontFamily = headline, fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 22.sp),
        bodyLarge = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 22.sp),
        bodyMedium = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp),
        bodySmall = TextStyle(fontFamily = body, fontWeight = FontWeight.Normal, fontSize = 13.sp, lineHeight = 18.sp),
        labelLarge = TextStyle(fontFamily = body, fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp),
        labelMedium = TextStyle(fontFamily = mono, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.55.sp),
        labelSmall = TextStyle(fontFamily = mono, fontWeight = FontWeight.Medium, fontSize = 10.sp, lineHeight = 14.sp, letterSpacing = 0.4.sp),
    )
}

@Composable
fun HabitSheetTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    MaterialTheme(
        colorScheme = if (darkTheme) DarkColors else LightColors,
        typography = habitTypography(),
        content = content,
    )
}
