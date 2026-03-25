package com.tutushubham.pokidex.feature_onboarding

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.material3.FilterChip
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tutushubham.pokidex.core.domain.model.DayBlock

@Composable
fun DayStructureScreen(
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
            text = "Design your day",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "When do you usually have focused time?",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(24.dp))

        DayBlock.entries.forEach { block ->

            val minutes = state.dayBlocks[block]
            val enabled = minutes != null

            DayBlockItem(
                block = block,
                enabled = enabled,
                minutes = minutes ?: 60,
                onToggle = {
                    onEvent(
                        OnboardingContract.Event.BlockToggled(
                            block = block,
                            enabled = it
                        )
                    )
                },
                onMinutesChanged = {
                    onEvent(
                        OnboardingContract.Event.BlockMinutesChanged(
                            block = block,
                            minutes = it
                        )
                    )
                }
            )

            Spacer(Modifier.height(16.dp))
        }

        Spacer(Modifier.weight(1f))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedButton(onClick = onBack) {
                Text("Back")
            }

            Button(
                onClick = onNext,
                enabled = state.dayBlocks.isNotEmpty()
            ) {
                Text("Next")
            }
        }
    }
}

@Composable
fun DayBlockItem(
    block: DayBlock,
    enabled: Boolean,
    minutes: Int,
    onToggle: (Boolean) -> Unit,
    onMinutesChanged: (Int) -> Unit
) {
    Column {

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {

            Switch(
                checked = enabled,
                onCheckedChange = onToggle
            )

            Spacer(Modifier.width(12.dp))

            Text(
                text = block.displayName(),
                style = MaterialTheme.typography.titleMedium
            )
        }

        AnimatedVisibility(visible = enabled) {
            Column {
                Spacer(Modifier.height(8.dp))
                val options = listOf(30, 60, 90, 120, 180)
                val selected = options.minByOrNull { kotlin.math.abs(it - minutes) } ?: 60
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    options.forEach { option ->
                        FilterChip(
                            selected = selected == option,
                            onClick = { onMinutesChanged(option) },
                            label = { Text("${option}m") },
                            modifier = Modifier.sizeIn(minWidth = 48.dp)
                        )
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "$selected minutes",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
        }
    }
}

fun DayBlock.displayName(): String =
    when (this) {
        DayBlock.MORNING -> "Morning"
        DayBlock.DAY -> "Day"
        DayBlock.EVENING -> "Evening"
        DayBlock.NIGHT -> "Night"
    }
