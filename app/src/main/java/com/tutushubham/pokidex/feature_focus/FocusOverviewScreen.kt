package com.tutushubham.pokidex.feature_focus

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import com.tutushubham.pokidex.core.domain.entity.Focus
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.domain.model.FocusStrategy
import com.tutushubham.pokidex.ui.components.AppCard
import com.tutushubham.pokidex.ui.components.PrimaryButton
import com.tutushubham.pokidex.ui.components.SectionHeader
import com.tutushubham.pokidex.ui.components.WeeklyBarChart
import com.tutushubham.pokidex.ui.theme.AppShapes
import com.tutushubham.pokidex.ui.theme.AppSpacing
import com.tutushubham.pokidex.ui.theme.PokidexTheme
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun FocusOverviewScreen(
    state: FocusState,
    onEdit: () -> Unit
) {
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppSpacing.xl, vertical = AppSpacing.lg)
    ) {
        SectionHeader(
            overline = "FOCUS PROTOCOL",
            title = "Focus System Overview",
            subtitle = "Calibrate targets, momentum, and strategy for this domain."
        )

        Spacer(Modifier.height(AppSpacing.xxl))

        if (state.isLoading) {
            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        } else {
            AppCard {
                Text(
                    text = "Current active focus",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(AppSpacing.sm))
                Text(
                    text = state.currentFocusTitle ?: "No focus assigned yet",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(AppSpacing.xs))
                Text(
                    text = state.domain.name,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(Modifier.height(AppSpacing.xl))

            AppCard {
                Text(
                    text = "7-day momentum",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Projection from your focus strategy (next seven days).",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = AppSpacing.xs, bottom = AppSpacing.md)
                )
                WeeklyBarChart(
                    values = state.weeklyMomentum,
                    labels = weekLabelsFromToday()
                )
            }

            Spacer(Modifier.height(AppSpacing.xl))

            AppCard(containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)) {
                Text(
                    text = "Blueprint Mode",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(AppSpacing.sm))
                Text(
                    text = "Lock a structured plan: edit your focus list and strategy to define how Pokidex rotates emphasis across this domain before you confirm.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(Modifier.height(AppSpacing.lg))

            Text(
                text = "Strategy: ${state.strategy?.let { strategyName(it) } ?: "Not set"}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            state.error?.let { err ->
                Spacer(Modifier.height(AppSpacing.sm))
                Text(err, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(AppSpacing.xl))

            PrimaryButton(
                text = "Edit Focus Strategy",
                onClick = onEdit
            )
        }
    }
}

private fun weekLabelsFromToday(): List<String> {
    val today = LocalDate.now()
    return (0 until 7).map { i ->
        today.plusDays(i.toLong()).dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault())
    }
}

private fun strategyName(strategy: FocusStrategy): String = when (strategy) {
    is FocusStrategy.Manual -> "Manual"
    is FocusStrategy.Rotation -> "Alternate"
    is FocusStrategy.Weighted -> "Weighted"
    is FocusStrategy.DeadlineDriven -> "Deadline-driven"
}

@Preview(showBackground = true)
@Composable
private fun FocusOverviewScreenPreview() {
    PokidexTheme {
        FocusOverviewScreen(
            state = FocusState(
                domain = Domain.FITNESS,
                focuses = listOf(
                    Focus("f1", Domain.FITNESS, "Running", 1, null),
                    Focus("f2", Domain.FITNESS, "Yoga", 1, null)
                ),
                strategy = FocusStrategy.Manual,
                weeklyMomentum = listOf(0.4f, 0.7f, 0.55f, 0.9f, 0.6f, 0.75f, 0.5f),
                currentFocusTitle = "Running"
            ),
            onEdit = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FocusOverviewScreenLoadingPreview() {
    PokidexTheme {
        FocusOverviewScreen(
            state = FocusState(
                domain = Domain.STUDIES,
                isLoading = true
            ),
            onEdit = {}
        )
    }
}
