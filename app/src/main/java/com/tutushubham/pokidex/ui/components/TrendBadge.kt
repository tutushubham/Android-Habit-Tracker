package com.tutushubham.pokidex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.tutushubham.pokidex.core.engine.TrendDirection
import com.tutushubham.pokidex.ui.theme.AppSemanticColors
import com.tutushubham.pokidex.ui.theme.AppShapes
import com.tutushubham.pokidex.ui.theme.AppSpacing

@Composable
fun TrendBadge(
    trend: TrendDirection,
    modifier: Modifier = Modifier
) {
    val (symbol, color) = when (trend) {
        TrendDirection.UP -> "↑" to AppSemanticColors.momentum
        TrendDirection.DOWN -> "↓" to AppSemanticColors.deficit
        TrendDirection.FLAT -> "→" to AppSemanticColors.neutral
    }

    Text(
        text = symbol,
        style = MaterialTheme.typography.labelMedium,
        color = color,
        modifier = modifier
            .background(color.copy(alpha = 0.12f), AppShapes.pill)
            .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs)
    )
}

@Composable
fun ConfidenceBadge(
    label: String,
    confidence: Double,
    modifier: Modifier = Modifier
) {
    val color = when {
        confidence >= 0.8 -> AppSemanticColors.momentum
        confidence >= 0.5 -> AppSemanticColors.warning
        else -> AppSemanticColors.deficit
    }

    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = color,
        modifier = modifier
            .background(color.copy(alpha = 0.12f), AppShapes.pill)
            .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs)
    )
}
