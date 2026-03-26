package com.tutushubham.pokidex.feature_today.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.tutushubham.pokidex.feature_today.ActiveSessionUiModel
import com.tutushubham.pokidex.feature_today.PerformanceStatus
import com.tutushubham.pokidex.feature_today.PreviewData
import com.tutushubham.pokidex.ui.theme.AppShapes
import com.tutushubham.pokidex.ui.theme.AppSizes
import com.tutushubham.pokidex.ui.theme.AppSpacing
import com.tutushubham.pokidex.ui.theme.PokidexTheme

@Composable
fun PerformanceIndicator(
    model: ActiveSessionUiModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.xxl),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
    ) {
        PlannedTempoCard(plannedMinutes = model.plannedMinutes)
        ActualVelocityCard(
            elapsedMinutes = model.elapsedMinutes,
            plannedMinutes = model.plannedMinutes,
            performance = model.performance,
            performanceLabel = model.performanceLabel
        )
    }
}

@Composable
private fun PlannedTempoCard(
    plannedMinutes: Int,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = AppShapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(Modifier.padding(AppSpacing.xl)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = "PLANNED TEMPO",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(AppSpacing.xs))
                    Text(
                        text = formatTime(plannedMinutes),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(text = "🕐", fontSize = 20.sp)
            }
            Spacer(Modifier.height(AppSpacing.lg))
            LinearProgressIndicator(
                progress = { 1f },
                modifier = Modifier.fillMaxWidth().height(AppSizes.progressBarHeight),
                color = MaterialTheme.colorScheme.primaryContainer,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            )
        }
    }
}

@Composable
private fun ActualVelocityCard(
    elapsedMinutes: Int,
    plannedMinutes: Int,
    performance: PerformanceStatus,
    performanceLabel: String,
    modifier: Modifier = Modifier
) {
    val velocityFraction = if (plannedMinutes > 0)
        (elapsedMinutes.toFloat() / plannedMinutes).coerceIn(0f, 1f)
    else 0f

    val accentColor = when (performance) {
        PerformanceStatus.FASTER -> MaterialTheme.colorScheme.tertiary
        PerformanceStatus.ON_TRACK -> MaterialTheme.colorScheme.tertiary
        PerformanceStatus.SLOWER -> MaterialTheme.colorScheme.error
        PerformanceStatus.OVERTIME -> MaterialTheme.colorScheme.error
    }

    val trendIcon = when (performance) {
        PerformanceStatus.FASTER -> "📈"
        PerformanceStatus.ON_TRACK -> "📈"
        PerformanceStatus.SLOWER -> "📉"
        PerformanceStatus.OVERTIME -> "⏱️"
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = AppShapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Column(Modifier.padding(AppSpacing.xl)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column {
                    Text(
                        text = "ACTUAL VELOCITY",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        letterSpacing = 1.sp
                    )
                    Spacer(Modifier.height(AppSpacing.xs))
                    Text(
                        text = formatTime(elapsedMinutes),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
                Text(text = trendIcon, fontSize = 20.sp)
            }
            Spacer(Modifier.height(AppSpacing.lg))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                LinearProgressIndicator(
                    progress = { velocityFraction },
                    modifier = Modifier.weight(1f).height(AppSizes.progressBarHeight),
                    color = accentColor,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                )
                Spacer(Modifier.width(AppSpacing.md))
                Text(
                    text = performanceLabel.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = accentColor,
                    fontSize = 10.sp,
                    letterSpacing = 0.5.sp
                )
            }
        }
    }
}

private fun formatTime(minutes: Int): String {
    val m = minutes.coerceAtLeast(0)
    return "%d:%02d".format(m, 0)
}

@Preview(showBackground = true)
@Composable
private fun PerformanceOnTrackPreview() {
    PokidexTheme {
        PerformanceIndicator(model = PreviewData.activeSessionOnTrack)
    }
}

@Preview(showBackground = true)
@Composable
private fun PerformanceOvertimePreview() {
    PokidexTheme {
        PerformanceIndicator(model = PreviewData.activeSessionOvertime)
    }
}
