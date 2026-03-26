package com.tutushubham.pokidex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tutushubham.pokidex.ui.theme.AppShapes
import com.tutushubham.pokidex.ui.theme.AppSizes
import com.tutushubham.pokidex.ui.theme.AppSpacing

@Composable
fun TimelineBlock(
    timeLabel: String,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    dotColor: Color = MaterialTheme.colorScheme.primary,
    isActive: Boolean = false,
    isLast: Boolean = false
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(AppSizes.iconXl)
        ) {
            Text(
                text = timeLabel,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(AppSpacing.xs))
            Box(
                modifier = Modifier
                    .size(AppSizes.dotSize)
                    .background(
                        color = if (isActive) dotColor else dotColor.copy(alpha = 0.4f),
                        shape = CircleShape
                    )
            )
            if (!isLast) {
                Box(
                    modifier = Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(MaterialTheme.colorScheme.outlineVariant)
                )
            }
        }
        Spacer(modifier = Modifier.width(AppSpacing.md))
        Surface(
            shape = AppShapes.medium,
            tonalElevation = if (isActive) AppSpacing.sm else AppSpacing.xs,
            modifier = Modifier
                .weight(1f)
                .padding(bottom = AppSpacing.md)
        ) {
            Column(modifier = Modifier.padding(AppSpacing.lg)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal
                )
                Spacer(modifier = Modifier.height(AppSpacing.xs))
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
