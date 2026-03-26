package com.tutushubham.pokidex.feature_onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.ui.components.AppCard
import com.tutushubham.pokidex.ui.components.PrimaryButton
import com.tutushubham.pokidex.ui.components.SecondaryButton
import com.tutushubham.pokidex.ui.components.SectionHeader
import com.tutushubham.pokidex.ui.theme.AppShapes
import com.tutushubham.pokidex.ui.theme.AppSpacing

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
            .padding(AppSpacing.lg)
    ) {
        SectionHeader(
            overline = "Personalize your sanctuary",
            title = "Define your\nFocus Areas",
            subtitle = "Select the areas of your life you want to cultivate. We'll tailor your sanctuary to these priorities."
        )

        Spacer(Modifier.height(AppSpacing.xxl))

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.xxl)
        ) {
            items(selectedDomains, key = { it.name }) { domain ->
                val focuses = state.focuses[domain].orEmpty()
                DomainFocusCard(
                    domain = domain,
                    focuses = focuses,
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
                enabled = validateFocuses(state),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun DomainFocusCard(
    domain: Domain,
    focuses: List<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit
) {
    AppCard(
        shape = AppShapes.large,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = domainEmoji(domain),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Spacer(Modifier.width(AppSpacing.md))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = domain.displayName(),
                            style = MaterialTheme.typography.titleLarge
                        )
                        Spacer(Modifier.height(AppSpacing.xs))
                        Text(
                            text = domainDescription(domain),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.width(AppSpacing.sm))
                Surface(
                    shape = AppShapes.pill,
                    color = MaterialTheme.colorScheme.primaryContainer
                ) {
                    Text(
                        text = focuses.size.toString(),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(
                            horizontal = AppSpacing.md,
                            vertical = AppSpacing.xs
                        )
                    )
                }
            }

            if (focuses.isNotEmpty()) {
                Spacer(Modifier.height(AppSpacing.md))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
                ) {
                    focuses.forEach { focus ->
                        FocusChip(
                            label = focus,
                            onRemove = { onRemove(focus) }
                        )
                    }
                }
            }

            Spacer(Modifier.height(AppSpacing.md))
            AddFocusInput(onAdd = onAdd)
    }
}

@Composable
private fun FocusChip(
    label: String,
    onRemove: () -> Unit
) {
    AssistChip(
        onClick = {},
        label = { Text(label) },
        shape = AppShapes.pill,
        colors = AssistChipDefaults.assistChipColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerHighest
        ),
        trailingIcon = {
            IconButton(
                onClick = onRemove,
                modifier = Modifier.padding(end = AppSpacing.xs)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Remove"
                )
            }
        }
    )
}

@Composable
private fun AddFocusInput(
    onAdd: (String) -> Unit
) {
    var text by remember { mutableStateOf("") }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        OutlinedTextField(
            value = text,
            onValueChange = { text = it },
            modifier = Modifier.weight(1f),
            placeholder = { Text("Add focus") },
            singleLine = true,
            shape = AppShapes.medium
        )

        Spacer(Modifier.width(AppSpacing.sm))

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

private fun domainEmoji(domain: Domain): String = when (domain) {
    Domain.STUDIES -> "📚"
    Domain.FITNESS -> "💪"
    Domain.WORK -> "💼"
    Domain.HOBBY -> "🎯"
}

private fun domainDescription(domain: Domain): String = when (domain) {
    Domain.STUDIES -> "Master new skills with deep focus sessions"
    Domain.FITNESS -> "Build consistency through physical wellness habits"
    Domain.WORK -> "Optimize your professional workflow"
    Domain.HOBBY -> "Dedicate time to your creative passions"
}

private fun validateFocuses(state: OnboardingContract.State): Boolean {
    val domains = state.goals.map { it.domain }.toSet()
    return domains.all { domain ->
        state.focuses[domain]?.isNotEmpty() == true
    }
}
