package com.tutushubham.pokidex.feature_today.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.ui.tooling.preview.Preview
import com.tutushubham.pokidex.core.engine.IntentProgress
import com.tutushubham.pokidex.feature_today.PreviewData
import com.tutushubham.pokidex.ui.theme.AppShapes
import com.tutushubham.pokidex.ui.theme.AppSizes
import com.tutushubham.pokidex.ui.theme.AppSpacing
import com.tutushubham.pokidex.ui.theme.PokidexTheme

@Composable
fun ProgressSection(
    progressList: List<IntentProgress>,
    onGoalClick: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (progressList.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.xl)
    ) {
        Text(
            text = "Goal Momentum",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(AppSpacing.md))
        progressList.forEach { progress ->
            ProgressCard(
                progress = progress,
                modifier = Modifier.clickable { onGoalClick(progress.intentId) }
            )
            Spacer(Modifier.height(AppSpacing.md))
        }
    }
}

@Composable
fun ProgressCard(
    progress: IntentProgress,
    modifier: Modifier = Modifier
) {
    val statusColor = when {
        progress.isCritical -> MaterialTheme.colorScheme.error
        progress.isBehind -> MaterialTheme.colorScheme.error
        else -> MaterialTheme.colorScheme.primary
    }
    val chipColor = when {
        progress.isCritical -> MaterialTheme.colorScheme.errorContainer
        progress.isBehind -> MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.primaryContainer
    }
    val chipTextColor = when {
        progress.isCritical || progress.isBehind -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onPrimaryContainer
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = AppShapes.large,
        tonalElevation = 0.dp,
        border = null,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(Modifier.padding(AppSpacing.lg)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = progress.title,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${progress.daysRemaining} days left",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Surface(
                    shape = AppShapes.pill,
                    color = chipColor
                ) {
                    Text(
                        text = statusLabel(progress),
                        modifier = Modifier.padding(horizontal = AppSpacing.sm + AppSpacing.xs, vertical = AppSpacing.xs),
                        style = MaterialTheme.typography.labelSmall,
                        color = chipTextColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Spacer(Modifier.height(AppSpacing.md))

            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "Progress",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    "${progress.completedUnits}/${progress.targetCount} done",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Spacer(Modifier.height(AppSpacing.xs + AppSpacing.xs))
            LinearProgressIndicator(
                progress = { progress.progressFraction },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AppSizes.progressBarHeight),
                color = statusColor,
                trackColor = statusColor.copy(alpha = 0.15f),
            )

            if (progress.isBehind || progress.isCritical) {
                Spacer(Modifier.height(AppSpacing.md))
                Text(
                    text = "DEFICIT PER DAY",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "+%.1f units".format(progress.deficit),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.ExtraBold
                )
            } else if (progress.currentPace > 0) {
                Spacer(Modifier.height(AppSpacing.md))
                Text(
                    text = "VELOCITY",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = if (progress.currentPace >= progress.requiredUnitsPerDay) "Steady" else "Slow",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}

private fun statusLabel(progress: IntentProgress): String = when {
    progress.isCritical -> "CRITICAL"
    progress.isBehind -> "BEHIND"
    else -> "ON TRACK"
}

@Preview(showBackground = true)
@Composable
private fun ProgressSectionPreview() {
    PokidexTheme {
        ProgressSection(progressList = PreviewData.allProgress)
    }
}

@Preview(showBackground = true)
@Composable
private fun ProgressCardCriticalPreview() {
    PokidexTheme {
        ProgressCard(progress = PreviewData.progressCritical)
    }
}

@Preview(showBackground = true)
@Composable
private fun ProgressCardOnTrackPreview() {
    PokidexTheme {
        ProgressCard(progress = PreviewData.progressOnTrack)
    }
}
