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
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tutushubham.pokidex.core.domain.model.Domain

@Composable
fun DomainSelectionScreen(
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
            text = "Choose your focus areas",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "You can change these later.",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(24.dp))

        val domainsFromGoals = state.goals.map { it.domain }.toSet()

        Domain.entries.forEach { domain ->

            val selected = domain in domainsFromGoals

            DomainItem(
                domain = domain,
                selected = selected,
                onToggle = { }
            )

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

            Button(
                onClick = onNext,
                enabled = state.goals.isNotEmpty()
            ) {
                Text("Next")
            }
        }
    }
}

@Composable
fun DomainItem(
    domain: Domain,
    selected: Boolean,
    onToggle: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        tonalElevation = if (selected) 4.dp else 0.dp,
        onClick = { onToggle(!selected) }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {

            Checkbox(
                checked = selected,
                onCheckedChange = onToggle
            )

            Spacer(Modifier.width(12.dp))

            Column {
                Text(
                    text = domain.displayName(),
                    style = MaterialTheme.typography.titleMedium
                )

                Text(
                    text = domain.description(),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}

fun Domain.displayName(): String =
    when (this) {
        Domain.STUDIES -> "Studies"
        Domain.FITNESS -> "Fitness"
        Domain.HOBBY -> "Hobby"
        Domain.WORK -> "Work"
    }

fun Domain.description(): String =
    when (this) {
        Domain.STUDIES -> "Learning, coding, preparation"
        Domain.FITNESS -> "Running, gym, health goals"
        Domain.HOBBY -> "Guitar, creative work"
        Domain.WORK -> "Professional growth"
    }
