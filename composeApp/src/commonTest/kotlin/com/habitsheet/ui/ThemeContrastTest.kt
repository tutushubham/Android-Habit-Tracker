package com.habitsheet.ui

import androidx.compose.material3.ColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertTrue

/** WCAG 2.x AA: 4.5:1 for text, 3:1 for the boundary of a control. Fails when a token is changed below that. */
class ThemeContrastTest {
    private fun ratio(a: Color, b: Color): Double {
        val x = a.luminance().toDouble()
        val y = b.luminance().toDouble()
        return (maxOf(x, y) + 0.05) / (minOf(x, y) + 0.05)
    }

    private fun check(name: String, fg: Color, bg: Color, minimum: Double) {
        val value = ratio(fg, bg)
        assertTrue(value >= minimum, "$name: contrast ${kotlin.math.round(value * 100) / 100} is below $minimum")
    }

    private fun verify(label: String, c: ColorScheme) {
        val text = 4.5
        val boundary = 3.0
        check("$label onSurface/surface", c.onSurface, c.surface, text)
        check("$label onBackground/background", c.onBackground, c.background, text)
        check("$label onSurfaceVariant/background", c.onSurfaceVariant, c.background, text)
        check("$label onSurfaceVariant/surfaceVariant", c.onSurfaceVariant, c.surfaceVariant, text)
        check("$label primary/background", c.primary, c.background, text)
        check("$label onPrimary/primary", c.onPrimary, c.primary, text)
        check("$label onPrimaryContainer/primaryContainer", c.onPrimaryContainer, c.primaryContainer, text)
        check("$label secondary/background", c.secondary, c.background, text)
        check("$label tertiary/background", c.tertiary, c.background, text)
        check("$label error/background", c.error, c.background, text)
        check("$label error/error container", c.error, c.error.copy(alpha = 0.1f).compositeOver(c.background), text)
        check("$label outline/background", c.outline, c.background, boundary)
        check("$label outline/surfaceVariant", c.outline, c.surfaceVariant, boundary)
    }

    @Test
    fun lightTokensMeetAa() = verify("light", LightColors)

    @Test
    fun darkTokensMeetAa() = verify("dark", DarkColors)
}
