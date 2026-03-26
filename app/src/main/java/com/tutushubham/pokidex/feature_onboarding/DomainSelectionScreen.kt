package com.tutushubham.pokidex.feature_onboarding

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.ui.components.AppCard
import com.tutushubham.pokidex.ui.components.MetricRing
import com.tutushubham.pokidex.ui.components.PrimaryButton
import com.tutushubham.pokidex.ui.components.SecondaryButton
import com.tutushubham.pokidex.ui.theme.AppShapes
import com.tutushubham.pokidex.ui.theme.AppSpacing
import com.tutushubham.pokidex.ui.theme.AppSizes

@Composable
fun DomainSelectionScreen(
    state: OnboardingContract.State,
    onEvent: (OnboardingContract.Event) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val domainsFromGoals = state.goals.map { it.domain }.toSet()
    val totalGoals = state.goals.size.coerceAtLeast(1)
    val counts = Domain.entries.associateWith { d ->
        state.goals.count { it.domain == d }
    }
    val ringScroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(AppSpacing.lg)
    ) {

        Text(
            text = "Choose your focus areas",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(Modifier.height(AppSpacing.sm))

        Text(
            text = "You can change these later.",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant
        )

        Spacer(Modifier.height(AppSpacing.xxl))

        if (state.goals.isNotEmpty()) {
            Text(
                text = "Goal energy distribution",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(AppSpacing.sm))
            Text(
                text = "Share of your goals per domain — we use this to balance upcoming sessions.",
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
            Spacer(Modifier.height(AppSpacing.lg))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(ringScroll),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.xl)
            ) {
                Domain.entries.forEach { domain ->
                    val n = counts[domain] ?: 0
                    val fraction = n.toFloat() / totalGoals.toFloat()
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        MetricRing(
                            progress = fraction,
                            label = "${(fraction * 100).toInt()}%",
                            size = AppSizes.ringSmall,
                            strokeWidth = AppSpacing.sm,
                            activeColor = when (domain) {
                                Domain.STUDIES -> scheme.primary
                                Domain.FITNESS -> scheme.tertiary
                                Domain.HOBBY -> scheme.secondary
                                Domain.WORK -> scheme.primaryContainer
                            },
                            sublabel = domain.displayName()
                        )
                        Spacer(Modifier.height(AppSpacing.xs))
                        Text(
                            text = "$n goal${if (n == 1) "" else "s"}",
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            Spacer(Modifier.height(AppSpacing.xxl))
        }

        Text(
            text = "Your domains",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(AppSpacing.md))

        Domain.entries.forEach { domain ->

            val selected = domain in domainsFromGoals

            DomainItem(
                domain = domain,
                selected = selected,
                onToggle = { }
            )

            Spacer(Modifier.height(AppSpacing.md))
        }

        Spacer(Modifier.weight(1f))

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
                enabled = state.goals.isNotEmpty(),
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun DomainItem(
    domain: Domain,
    selected: Boolean,
    onToggle: (Boolean) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    AppCard(
        shape = AppShapes.medium,
        containerColor = if (selected) scheme.primaryContainer.copy(alpha = 0.35f) else scheme.surface,
        onClick = { onToggle(!selected) }
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Checkbox(
                checked = selected,
                onCheckedChange = onToggle
            )

            Spacer(Modifier.width(AppSpacing.md))

            Column {
                Text(
                    text = domain.displayName(),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                )

                Text(
                    text = domain.description(),
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onSurfaceVariant
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
