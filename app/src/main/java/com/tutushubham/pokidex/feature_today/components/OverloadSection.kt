package com.tutushubham.pokidex.feature_today.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.tutushubham.pokidex.core.engine.IntentProgress
import com.tutushubham.pokidex.feature_today.PreviewData
import com.tutushubham.pokidex.ui.theme.AppShapes
import com.tutushubham.pokidex.ui.theme.AppSpacing
import com.tutushubham.pokidex.ui.theme.PokidexTheme

@Composable
fun OverloadSection(
    overloadedIntentIds: List<String>,
    maxSeverity: Double?,
    progressList: List<IntentProgress>,
    onAdjustDeadlines: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (overloadedIntentIds.isEmpty()) return

    val severity = maxSeverity ?: 1.0
    val overloadedProgress = progressList.filter { it.intentId in overloadedIntentIds }

    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.xl),
        shape = AppShapes.large,
        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
    ) {
        Column(Modifier.padding(AppSpacing.xl)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "⚠️",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "  Cognitive Overload Detected",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(AppSpacing.sm))

            Text(
                text = overloadSummary(severity),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onErrorContainer
            )

            if (overloadedProgress.isNotEmpty()) {
                Spacer(Modifier.height(AppSpacing.md))
                overloadedProgress.forEach { progress ->
                    OverloadDetailRow(progress)
                    Spacer(Modifier.height(AppSpacing.xs + AppSpacing.xs))
                }
            }

            Spacer(Modifier.height(AppSpacing.lg))

            Button(
                onClick = { overloadedIntentIds.firstOrNull()?.let(onAdjustDeadlines) },
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.medium,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text("Adjust Deadlines", fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun OverloadDetailRow(
    progress: IntentProgress,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = AppShapes.small,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${progress.title} Demand",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "%.1f/day needed".format(progress.requiredUnitsPerDay),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.error
            )
        }
    }
}

private fun overloadSummary(severity: Double): String = when {
    severity >= 3.0 -> "Critical: your capacity is far below what's needed to meet deadlines."
    severity >= 1.5 -> "Your current capacity cannot meet some deadlines. Consider adjusting."
    else -> "Your current capacity may not meet some deadlines."
}

@Preview(showBackground = true)
@Composable
private fun OverloadSectionPreview() {
    PokidexTheme {
        OverloadSection(
            overloadedIntentIds = listOf("i1"),
            maxSeverity = 2.1,
            progressList = PreviewData.allProgress
        )
    }
}
