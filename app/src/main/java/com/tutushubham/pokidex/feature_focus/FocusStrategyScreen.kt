package com.tutushubham.pokidex.feature_focus

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tutushubham.pokidex.core.domain.entity.Focus
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.domain.model.FocusStrategy
import com.tutushubham.pokidex.ui.theme.PokidexTheme

@Composable
fun FocusStrategyScreen(
    focuses: List<Focus>,
    selected: FocusStrategy?,
    onStrategySelected: (FocusStrategy) -> Unit,
    onNext: () -> Unit
) {
    Column(Modifier.padding(16.dp)) {

        Text(
            text = "How should this domain rotate?",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(Modifier.height(16.dp))

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

        Spacer(Modifier.height(24.dp))

        Button(onClick = onNext, enabled = selected != null) {
            Text("Next")
        }
    }
}

@Composable
fun StrategyOption(
    title: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Spacer(Modifier.width(8.dp))
        Text(title)
    }
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
            onNext = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FocusStrategyScreenRotationSelectedPreview() {
    PokidexTheme {
        FocusStrategyScreen(
            focuses = listOf(
                Focus("f1", Domain.FITNESS, "Running", 1, null),
                Focus("f2", Domain.FITNESS, "Yoga", 1, null)
            ),
            selected = FocusStrategy.Rotation(order = listOf("f1", "f2")),
            onStrategySelected = {},
            onNext = {}
        )
    }
}
