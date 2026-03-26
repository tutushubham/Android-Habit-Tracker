package com.tutushubham.pokidex.ui.components

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.tutushubham.pokidex.ui.theme.AppShapes
import com.tutushubham.pokidex.ui.theme.AppSizes
import com.tutushubham.pokidex.ui.theme.AppSpacing

@Composable
fun AppChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    selectedContainerColor: Color = MaterialTheme.colorScheme.primary,
    selectedContentColor: Color = MaterialTheme.colorScheme.onPrimary,
    unselectedContainerColor: Color = MaterialTheme.colorScheme.surfaceContainerHighest,
    unselectedContentColor: Color = MaterialTheme.colorScheme.onSurfaceVariant
) {
    val bg = if (selected) selectedContainerColor else unselectedContainerColor
    val fg = if (selected) selectedContentColor else unselectedContentColor
    val alpha = if (enabled) 1f else 0.38f

    Surface(
        onClick = { if (enabled) onClick() },
        modifier = modifier.height(AppSizes.chipHeight),
        shape = AppShapes.pill,
        color = bg.copy(alpha = alpha)
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = AppSpacing.lg, vertical = AppSpacing.sm),
            color = fg.copy(alpha = alpha),
            style = MaterialTheme.typography.labelMedium,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
    }
}
