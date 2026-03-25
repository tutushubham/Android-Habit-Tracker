package com.tutushubham.pokidex.feature_onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.domain.model.FocusStrategy

@Composable
fun StrategySetupScreen(
    state: OnboardingContract.State,
    onEvent: (OnboardingContract.Event) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(16.dp)
    ) {

        Text(
            text = "How should your focus rotate?",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Choose how each domain should behave.",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(24.dp))

        LazyColumn(
            modifier = Modifier.weight(1f)
        ) {
            state.goals.map { it.domain }.distinct().forEach { domain ->

                item {
                    DomainStrategySection(
                        domain = domain,
                        focuses = state.focuses[domain].orEmpty(),
                        selectedStrategy = state.strategies[domain],
                        onStrategySelected = { strategy ->
                            onEvent(
                                OnboardingContract.Event.StrategySelected(
                                    domain = domain,
                                    strategy = strategy
                                )
                            )
                        }
                    )

                    Spacer(Modifier.height(24.dp))
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedButton(onClick = onBack) {
                Text("Back")
            }

            Button(onClick = onNext) {
                Text("Next")
            }
        }
    }
}

@Composable
fun DomainStrategySection(
    domain: Domain,
    focuses: List<String>,
    selectedStrategy: FocusStrategy?,
    onStrategySelected: (FocusStrategy) -> Unit
) {
    Column {

        Text(
            text = domain.displayName(),
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(Modifier.height(8.dp))

        StrategyOption(
            title = "Manual",
            description = "You choose DSA when you want, Android another day.",
            selected = selectedStrategy is FocusStrategy.Manual,
            onClick = { onStrategySelected(FocusStrategy.Manual) }
        )

        StrategyOption(
            title = "Alternate",
            description = "Mon DSA → Tue Android → Wed DSA (rotates by day).",
            selected = selectedStrategy is FocusStrategy.Rotation,
            onClick = {
                onStrategySelected(
                    FocusStrategy.Rotation(
                        order = focuses
                    )
                )
            }
        )

        StrategyOption(
            title = "Weighted",
            description = "DSA appears more often than Android (set importance 1–5).",
            selected = selectedStrategy is FocusStrategy.Weighted,
            onClick = {
                onStrategySelected(
                    FocusStrategy.Weighted(
                        weights = focuses.associateWith { 1 }
                    )
                )
            }
        )

        StrategyOption(
            title = "Deadline-driven",
            description = "Upcoming deadlines override",
            selected = selectedStrategy is FocusStrategy.DeadlineDriven,
            onClick = {
                onStrategySelected(FocusStrategy.DeadlineDriven)
            }
        )

        Spacer(Modifier.height(12.dp))

        if (selectedStrategy is FocusStrategy.Rotation) {
            RotationEditor(
                focuses = focuses,
                order = selectedStrategy.order,
                onOrderChanged = {
                    onStrategySelected(
                        selectedStrategy.copy(order = it)
                    )
                }
            )
        }

        if (selectedStrategy is FocusStrategy.Weighted) {
            WeightedEditor(
                focuses = focuses,
                weights = selectedStrategy.weights,
                onWeightsChanged = {
                    onStrategySelected(
                        selectedStrategy.copy(weights = it)
                    )
                }
            )
        }
    }
}

@Composable
fun StrategyOption(
    title: String,
    description: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        tonalElevation = if (selected) 4.dp else 0.dp,
        shape = RoundedCornerShape(12.dp),
        onClick = onClick
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(4.dp))
            Text(description, style = MaterialTheme.typography.bodySmall)
        }
    }

    Spacer(Modifier.height(8.dp))
}

@Composable
fun RotationEditor(
    focuses: List<String>,
    order: List<String>,
    onOrderChanged: (List<String>) -> Unit
) {
    Column {

        Text("Rotation order", style = MaterialTheme.typography.labelMedium)

        Spacer(Modifier.height(8.dp))

        focuses.forEach { focus ->
            Text("• $focus")
        }

        LaunchedEffect(focuses, order) {
            val preservedOrder = order.filter { it in focuses }
            val newFocuses = focuses.filter { it !in preservedOrder }
            val newOrder = preservedOrder + newFocuses
            if (newOrder != order) {
                onOrderChanged(newOrder)
            }
        }
    }
}

private val WEIGHT_OPTIONS = listOf(1, 2, 3, 4, 5)

@Composable
fun WeightedEditor(
    focuses: List<String>,
    weights: Map<String, Int>,
    onWeightsChanged: (Map<String, Int>) -> Unit
) {
    Column {
        Text("Set importance (1–5)", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(8.dp))

        focuses.forEach { focus ->
            val weight = (weights[focus] ?: 1).coerceIn(1, 5)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(focus, modifier = Modifier.weight(1f))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    WEIGHT_OPTIONS.forEach { value ->
                        val selected = weight == value
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            tonalElevation = if (selected) 4.dp else 0.dp,
                            onClick = {
                                val updated = weights.toMutableMap()
                                updated[focus] = value
                                onWeightsChanged(updated)
                            }
                        ) {
                            Text(
                                text = "$value",
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
        }
    }
}
