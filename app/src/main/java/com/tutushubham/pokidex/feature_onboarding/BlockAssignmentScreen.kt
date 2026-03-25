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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tutushubham.pokidex.core.domain.model.DayBlock
import com.tutushubham.pokidex.core.domain.model.Domain

@Composable
fun BlockAssignmentScreen(
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
            text = "Assign domains to your blocks",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Choose one focus area per time block (e.g. Morning = Studies, Evening = Fitness).",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(24.dp))

        state.dayBlocks.forEach { (block, minutes) ->
            val currentDomain = state.blockToDomain[block]

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${block.displayName()} ($minutes min)",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.width(140.dp)
                )
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    state.goals.map { it.domain }.toSet().forEach { domain ->
                        val selected = currentDomain == domain
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            tonalElevation = if (selected) 4.dp else 0.dp,
                            onClick = {
                                onEvent(
                                    OnboardingContract.Event.BlockDomainAssigned(
                                        block = block,
                                        domain = domain
                                    )
                                )
                            }
                        ) {
                            Text(
                                text = domain.displayName(),
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }

        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedButton(onClick = onBack) {
                Text("Back")
            }

            val allAssigned = state.dayBlocks.keys.all { state.blockToDomain[it] != null }
            Button(
                onClick = onNext,
                enabled = allAssigned
            ) {
                Text("Next")
            }
        }
    }
}
