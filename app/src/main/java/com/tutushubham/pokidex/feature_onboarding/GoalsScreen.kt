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
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.text.font.FontWeight
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.ui.components.AppCard
import com.tutushubham.pokidex.ui.components.AppChip
import com.tutushubham.pokidex.ui.components.PrimaryButton
import com.tutushubham.pokidex.ui.components.SecondaryButton
import com.tutushubham.pokidex.ui.theme.AppShapes
import com.tutushubham.pokidex.ui.theme.AppSpacing
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID

private data class GoalTemplate(
    val title: String,
    val domain: Domain,
    val daysToDeadline: Long
)

private val goalTemplates = listOf(
    GoalTemplate("Finish a course or certification", Domain.STUDIES, 90),
    GoalTemplate("Run a 5K / build a fitness habit", Domain.FITNESS, 60),
    GoalTemplate("Ship a side project", Domain.WORK, 45),
    GoalTemplate("Learn guitar / creative practice", Domain.HOBBY, 120)
)

@Composable
fun GoalsScreen(
    state: OnboardingContract.State,
    onEvent: (OnboardingContract.Event) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val canProceed = state.goals.isNotEmpty() && state.goals.all { it.isValid }
    val today = LocalDate.now()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(AppSpacing.lg)
    ) {
        Text(
            text = "What are you working toward?",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(Modifier.height(AppSpacing.sm))

        Text(
            text = "Add the goals you want to finish in the next few months.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(AppSpacing.xxl))

        Text(
            text = "Start from a template",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(AppSpacing.sm))
        Text(
            text = "Tap to add — you can edit titles and deadlines anytime.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(AppSpacing.md))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            goalTemplates.take(2).forEach { template ->
                FilterChip(
                    selected = false,
                    onClick = {
                        onEvent(
                            OnboardingContract.Event.GoalAdded(
                                OnboardingGoal(
                                    id = UUID.randomUUID().toString(),
                                    title = template.title,
                                    domain = template.domain,
                                    deadline = today.plusDays(template.daysToDeadline)
                                )
                            )
                        )
                    },
                    label = {
                        Text(
                            template.title,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 2
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }
        Spacer(Modifier.height(AppSpacing.sm))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            goalTemplates.drop(2).forEach { template ->
                FilterChip(
                    selected = false,
                    onClick = {
                        onEvent(
                            OnboardingContract.Event.GoalAdded(
                                OnboardingGoal(
                                    id = UUID.randomUUID().toString(),
                                    title = template.title,
                                    domain = template.domain,
                                    deadline = today.plusDays(template.daysToDeadline)
                                )
                            )
                        )
                    },
                    label = {
                        Text(
                            template.title,
                            style = MaterialTheme.typography.labelMedium,
                            maxLines = 2
                        )
                    },
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(Modifier.height(AppSpacing.xxl))

        AppCard(
            shape = AppShapes.medium,
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        ) {
            Text(
                text = "Milestone hints",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(AppSpacing.sm))
            Text(
                text = "• Set a deadline at least a few days ahead so the plan stays realistic.\n" +
                    "• Add a measurable target when you care about counts (pages, songs, modules).\n" +
                    "• One clear goal per card works best — split big goals into separate entries.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(AppSpacing.lg))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            items(state.goals) { goal ->
                GoalCard(
                    goal = goal,
                    onUpdate = { onEvent(OnboardingContract.Event.GoalUpdated(it)) },
                    onRemove = { onEvent(OnboardingContract.Event.GoalRemoved(goal.id)) }
                )
            }

            item {
                SecondaryButton(
                    text = "+ Add another goal",
                    onClick = { onEvent(OnboardingContract.Event.AddGoalRequested) }
                )
            }
        }

        Spacer(Modifier.height(AppSpacing.lg))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            SecondaryButton(
                text = "Back",
                onClick = onBack,
                modifier = Modifier.weight(1f)
            )
            PrimaryButton(
                text = "Next",
                onClick = onNext,
                enabled = canProceed,
                modifier = Modifier.weight(1f)
            )
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

    AppCard(
        shape = AppShapes.medium,
        modifier = Modifier.fillMaxWidth()
    ) {
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
                    singleLine = true,
                    shape = AppShapes.medium
                )
                Spacer(Modifier.padding(AppSpacing.sm))
                TextButton(onClick = onRemove) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            }

            Spacer(Modifier.height(AppSpacing.md))

            Text("Domain", style = MaterialTheme.typography.labelMedium)
            Spacer(Modifier.height(AppSpacing.xs))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                Domain.entries.forEach { domain ->
                    val selected = goal.domain == domain
                    AppChip(
                        label = domain.displayName(),
                        selected = selected,
                        onClick = { onUpdate(goal.copy(domain = domain)) }
                    )
                }
            }

            Spacer(Modifier.height(AppSpacing.md))

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

            Spacer(Modifier.height(AppSpacing.sm))

            if (showTargetCount) {
                OutlinedTextField(
                    value = goal.targetCount?.toString() ?: "",
                    onValueChange = {
                        val n = it.toIntOrNull()
                        onUpdate(goal.copy(targetCount = n))
                    },
                    label = { Text("Target count") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShapes.medium
                )
                Spacer(Modifier.height(AppSpacing.sm))
                OutlinedTextField(
                    value = goal.estimatedMinutesPerUnit?.toString() ?: "",
                    onValueChange = {
                        val n = it.toIntOrNull()?.coerceAtLeast(1)
                        onUpdate(goal.copy(estimatedMinutesPerUnit = n))
                    },
                    label = { Text("Est. min per unit (e.g. 25 for DSA, 180 for song)") },
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShapes.medium
                )
            } else {
                TextButton(onClick = { showTargetCount = true }) {
                    Text("+ Add measurable target")
                }
            }
    }
}
