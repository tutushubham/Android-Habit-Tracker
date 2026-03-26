package com.tutushubham.pokidex.feature_focus

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
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
import com.tutushubham.pokidex.ui.theme.AppShapes
import com.tutushubham.pokidex.ui.theme.AppSpacing
import com.tutushubham.pokidex.ui.theme.PokidexTheme
import kotlin.math.roundToInt

@Composable
fun FocusStrategyScreen(
    focuses: List<Focus>,
    selected: FocusStrategy?,
    onStrategySelected: (FocusStrategy) -> Unit,
    onWeightsUpdated: (Map<String, Int>) -> Unit,
    onNext: () -> Unit
) {
    Column(
        Modifier
            .verticalScroll(rememberScrollState())
            .padding(AppSpacing.xl)
    ) {
        SectionHeader(
            overline = "STRATEGY",
            title = "Strategy Configuration",
            subtitle = "How should this domain rotate between targets?"
        )

        Spacer(Modifier.height(AppSpacing.lg))

        StrategyOption(
            title = "Manual",
            selected = selected is FocusStrategy.Manual
        ) {
            onStrategySelected(FocusStrategy.Manual)
        }

        StrategyOption(
            title = "Alternate",
            selected = selected is FocusStrategy.Rotation
        ) {
            onStrategySelected(
                FocusStrategy.Rotation(
                    order = focuses.map { it.id }
                )
            )
        }

        StrategyOption(
            title = "Weighted",
            selected = selected is FocusStrategy.Weighted
        ) {
            onStrategySelected(
                FocusStrategy.Weighted(
                    weights = focuses.associate { it.id to 1 }
                )
            )
        }

        StrategyOption(
            title = "Deadline-driven",
            selected = selected is FocusStrategy.DeadlineDriven
        ) {
            onStrategySelected(FocusStrategy.DeadlineDriven)
        }

        val weighted = selected as? FocusStrategy.Weighted
        if (weighted != null && focuses.isNotEmpty()) {
            Spacer(Modifier.height(AppSpacing.xl))
            Text(
                text = "Domain weights",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Text(
                text = "Higher weight = more airtime in the rotation mix.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = AppSpacing.xs, bottom = AppSpacing.md)
            )
            focuses.forEach { focus ->
                val w = weighted.weights[focus.id] ?: 1
                Column(Modifier.padding(vertical = AppSpacing.sm)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = focus.name,
                            style = MaterialTheme.typography.bodyLarge
                        )
                        Text(
                            text = "$w",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Slider(
                        value = w.toFloat(),
                        onValueChange = { v ->
                            val next = v.roundToInt().coerceIn(1, 10)
                            onWeightsUpdated(
                                weighted.weights + (focus.id to next)
                            )
                        },
                        valueRange = 1f..10f,
                        steps = 8
                    )
                }
            }

            val efficiency = weightedAlgorithmEfficiencyPercent(weighted.weights, focuses.map { it.id })
            Spacer(Modifier.height(AppSpacing.lg))
            AppCard(
                shape = AppShapes.medium,
                containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
            ) {
                Text(
                    text = "Algorithm efficiency",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(Modifier.height(AppSpacing.xs))
                Text(
                    text = "$efficiency%",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Balanced weights improve predictable rotation. Extreme skew concentrates time on fewer targets.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = AppSpacing.sm)
                )
            }
        }

        Spacer(Modifier.height(AppSpacing.xl))

        AppCard(containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.35f)) {
            Text(
                text = "Deep work block",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(AppSpacing.sm))
            Text(
                text = "Pair this strategy with 60–90 minute uninterrupted blocks. Shorter fragments reduce the resolver's effective signal—protect calendar depth when weighted or alternate modes are active.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(AppSpacing.xxl))

        PrimaryButton(
            text = "Next",
            onClick = onNext,
            enabled = selected != null
        )
    }
}

@Composable
private fun StrategyOption(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(AppSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(AppSpacing.sm))
        Text(title)
    }
}

private fun weightedAlgorithmEfficiencyPercent(weights: Map<String, Int>, focusIds: List<String>): Int {
    if (focusIds.isEmpty()) return 100
    if (focusIds.size == 1) return 100
    val values = focusIds.map { weights[it]?.coerceIn(1, 10) ?: 1 }
    val sum = values.sum().toFloat().coerceAtLeast(1f)
    val shares = values.map { it / sum }
    val ideal = 1f / focusIds.size
    val meanSqDiff = shares.sumOf { d ->
        val x = (d - ideal).toDouble()
        x * x
    } / focusIds.size
    val maxSpread = (1f - ideal).toDouble()
    val maxMean = maxSpread * maxSpread
    val normalized = 1f - (meanSqDiff / maxMean).coerceIn(0.0, 1.0).toFloat()
    return (50f + 50f * normalized).roundToInt().coerceIn(50, 100)
}

@Preview(showBackground = true)
@Composable
private fun FocusStrategyScreenPreview() {
    PokidexTheme {
        FocusStrategyScreen(
            focuses = listOf(
                Focus("f1", Domain.FITNESS, "Running", 1, null),
                Focus("f2", Domain.FITNESS, "Yoga", 1, null)
            ),
            selected = FocusStrategy.Manual,
            onStrategySelected = {},
            onWeightsUpdated = {},
            onNext = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FocusStrategyScreenWeightedPreview() {
    PokidexTheme {
        FocusStrategyScreen(
            focuses = listOf(
                Focus("f1", Domain.FITNESS, "Running", 1, null),
                Focus("f2", Domain.FITNESS, "Yoga", 1, null)
            ),
            selected = FocusStrategy.Weighted(weights = mapOf("f1" to 3, "f2" to 2)),
            onStrategySelected = {},
            onWeightsUpdated = {},
            onNext = {}
        )
    }
}
