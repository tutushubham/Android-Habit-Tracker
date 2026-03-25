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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tutushubham.pokidex.core.domain.model.Domain
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

@Composable
fun GoalsScreen(
    state: OnboardingContract.State,
    onEvent: (OnboardingContract.Event) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val canProceed = state.goals.isNotEmpty() && state.goals.all { it.isValid }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(16.dp)
    ) {
        Text(
            text = "What are you working toward?",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(Modifier.height(8.dp))

        Text(
            text = "Add the goals you want to finish in the next few months.",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(24.dp))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(state.goals) { goal ->
                GoalCard(
                    goal = goal,
                    onUpdate = { onEvent(OnboardingContract.Event.GoalUpdated(it)) },
                    onRemove = { onEvent(OnboardingContract.Event.GoalRemoved(goal.id)) }
                )
            }

            item {
                OutlinedButton(
                    onClick = { onEvent(OnboardingContract.Event.AddGoalRequested) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("+ Add another goal")
                }
            }
        }

        Spacer(Modifier.height(16.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            OutlinedButton(onClick = onBack) {
                Text("Back")
            }
            Button(onClick = onNext, enabled = canProceed) {
                Text("Next")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GoalCard(
    goal: OnboardingGoal,
    onUpdate: (OnboardingGoal) -> Unit,
    onRemove: () -> Unit
) {
    var showDatePicker by remember { mutableStateOf(false) }
    var showTargetCount by remember { mutableStateOf(goal.targetCount != null) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        tonalElevation = 2.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = goal.title,
                    onValueChange = { onUpdate(goal.copy(title = it)) },
                    label = { Text("Title") },
                    modifier = Modifier.weight(1f),
                    singleLine = true
                )
                Spacer(Modifier.padding(8.dp))
                TextButton(onClick = onRemove) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(Modifier.height(12.dp))

            Text("Domain", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Domain.entries.forEach { domain ->
                    val selected = goal.domain == domain
                    FilterChip(
                        selected = selected,
                        onClick = { onUpdate(goal.copy(domain = domain)) },
                        label = { Text(domain.displayName()) }
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            TextButton(
                onClick = { showDatePicker = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("Deadline: ${goal.deadline}")
            }

            if (showDatePicker) {
                val datePickerState = rememberDatePickerState(
                    initialSelectedDateMillis = goal.deadline
                        .atStartOfDay(ZoneId.systemDefault())
                        .toInstant()
                        .toEpochMilli()
                )
                DatePickerDialog(
                    onDismissRequest = { showDatePicker = false },
                    confirmButton = {
                        TextButton(
                            onClick = {
                                datePickerState.selectedDateMillis?.let { millis ->
                                    val date = Instant.ofEpochMilli(millis)
                                        .atZone(ZoneId.systemDefault())
                                        .toLocalDate()
                                    onUpdate(goal.copy(deadline = date))
                                }
                                showDatePicker = false
                            }
                        ) {
                            Text("OK")
                        }
                    }
                ) {
                    DatePicker(state = datePickerState)
                }
            }

            Spacer(Modifier.height(8.dp))

            if (showTargetCount) {
                OutlinedTextField(
                    value = goal.targetCount?.toString() ?: "",
                    onValueChange = {
                        val n = it.toIntOrNull()
                        onUpdate(goal.copy(targetCount = n))
                    },
                    label = { Text("Target count") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = goal.estimatedMinutesPerUnit?.toString() ?: "",
                    onValueChange = {
                        val n = it.toIntOrNull()?.coerceAtLeast(1)
                        onUpdate(goal.copy(estimatedMinutesPerUnit = n))
                    },
                    label = { Text("Est. min per unit (e.g. 25 for DSA, 180 for song)") },
                    modifier = Modifier.fillMaxWidth()
                )
            } else {
                TextButton(onClick = { showTargetCount = true }) {
                    Text("+ Add measurable target")
                }
            }
        }
    }
}
