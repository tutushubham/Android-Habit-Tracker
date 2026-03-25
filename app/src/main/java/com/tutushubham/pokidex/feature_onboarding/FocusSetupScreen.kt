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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tutushubham.pokidex.core.domain.model.Domain

@Composable
fun FocusSetupScreen(
    state: OnboardingContract.State,
    onEvent: (OnboardingContract.Event) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val selectedDomains = state.goals.map { it.domain }.distinct()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(16.dp)
    ) {

        Text(
            text = "Define your focus areas",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "What do you usually work on inside each domain?",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(24.dp))

        LazyColumn(
            modifier = Modifier.weight(1f)
        ) {

            selectedDomains.forEach { domain ->

                item {
                    DomainFocusSection(
                        domain = domain,
                        focuses = state.focuses[domain].orEmpty(),
                        onAdd = { name ->
                            onEvent(
                                OnboardingContract.Event.FocusAdded(
                                    domain = domain,
                                    name = name
                                )
                            )
                        },
                        onRemove = { name ->
                            onEvent(
                                OnboardingContract.Event.FocusRemoved(
                                    domain = domain,
                                    name = name
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

            Button(
                onClick = onNext,
                enabled = validateFocuses(state)
            ) {
                Text("Next")
            }
        }
    }
}

@Composable
fun DomainFocusSection(
    domain: Domain,
    focuses: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    Column {

        Text(
            text = domain.displayName(),
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(Modifier.height(8.dp))

        focuses.forEach { focus ->
            FocusChip(
                label = focus,
                onRemove = { onRemove(focus) }
            )

            Spacer(Modifier.height(8.dp))
        }

        Spacer(Modifier.height(8.dp))

        AddFocusInput(
            onAdd = onAdd
        )
    }
}

@Composable
fun FocusChip(
    label: String,
    onRemove: () -> Unit
) {
    AssistChip(
        onClick = {},
        label = { Text(label) },
        trailingIcon = {
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove"
                )
            }
        }
    )
}

@Composable
fun AddFocusInput(
    onAdd: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }

    Row(
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("Add focus") },
            singleLine = true
        )

        Spacer(Modifier.width(8.dp))

        IconButton(
            onClick = {
                if (text.isNotBlank()) {
                    onAdd(text.trim())
                    text = ""
                }
            }
        ) {
            Icon(Icons.Default.Add, contentDescription = "Add")
        }
    }
}

private fun validateFocuses(state: OnboardingContract.State): Boolean {
    val domains = state.goals.map { it.domain }.toSet()
    return domains.all { domain ->
        state.focuses[domain]?.isNotEmpty() == true
    }
}
