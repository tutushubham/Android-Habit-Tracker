package com.tutushubham.pokidex.feature_focus

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.domain.model.FocusStrategy
import com.tutushubham.pokidex.ui.theme.PokidexTheme

@Composable
fun FocusOverviewScreen(
    state: FocusState,
    onEdit: () -> Unit
) {
    Column(Modifier.padding(16.dp)) {

        Text(
            text = state.domain.name,
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(Modifier.height(8.dp))

        if (state.isLoading) {
            CircularProgressIndicator(Modifier.align(Alignment.CenterHorizontally))
        } else {
            Text("Focuses:")
            state.focuses.forEach {
                Text("• ${it.name}")
            }

            Spacer(Modifier.height(12.dp))

            Text(
                text = "Strategy: ${state.strategy?.let { strategyName(it) } ?: "Not set"}"
            )

            state.error?.let { err ->
                Spacer(Modifier.height(8.dp))
                Text(err, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(16.dp))

            Button(onClick = onEdit) {
                Text("Edit Focus Strategy")
            }
        }
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
                    com.tutushubham.pokidex.core.domain.entity.Focus(
                        "f1",
                        Domain.FITNESS,
                        "Running",
                        1,
                        null
                    ),
                    com.tutushubham.pokidex.core.domain.entity.Focus(
                        "f2",
                        Domain.FITNESS,
                        "Yoga",
                        1,
                        null
                    )
                ),
                strategy = FocusStrategy.Manual
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
