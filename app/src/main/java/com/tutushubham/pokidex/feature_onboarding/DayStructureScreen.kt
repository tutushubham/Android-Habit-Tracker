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
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.tutushubham.pokidex.core.domain.model.DayBlock
import com.tutushubham.pokidex.ui.components.AppCard
import com.tutushubham.pokidex.ui.components.AppChip
import com.tutushubham.pokidex.ui.components.InsightCard
import com.tutushubham.pokidex.ui.components.PrimaryButton
import com.tutushubham.pokidex.ui.components.SecondaryButton
import com.tutushubham.pokidex.ui.theme.AppShapes
import com.tutushubham.pokidex.ui.theme.AppSpacing

@Composable
fun DayStructureScreen(
    state: OnboardingContract.State,
    onEvent: (OnboardingContract.Event) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val scroll = rememberScrollState()
    val totalMinutes = state.dayBlocks.values.sum()
    val enabledCount = state.dayBlocks.size

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(AppSpacing.lg)
    ) {

        Text(
            text = "Design your day",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(Modifier.height(AppSpacing.sm))

        Text(
            text = "When do you usually have focused time?",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant
        )

        Spacer(Modifier.height(AppSpacing.xxl))

        InsightCard(
            icon = "📊",
            title = "Structure analysis",
            subtitle = "Enable the blocks that match your life. We’ll spread sessions across them so energy follows your rhythm — not a rigid 9-to-5.",
            containerColor = scheme.primaryContainer,
            contentColor = scheme.onPrimaryContainer
        )

        Spacer(Modifier.height(AppSpacing.xxl))

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)
        ) {
            DayBlock.entries.forEach { block ->
                val minutes = state.dayBlocks[block]
                val enabled = minutes != null

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = AppShapes.medium,
                    tonalElevation = AppSpacing.xs,
                    color = if (enabled) {
                        scheme.surfaceContainerLow
                    } else {
                        scheme.surface
                    }
                ) {
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
                }
            }

            if (enabledCount > 0 && totalMinutes > 0) {
                AppCard(
                    shape = AppShapes.medium,
                    containerColor = scheme.surfaceContainerLow
                ) {
                    Text(
                        text = "Energy distribution hint",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(Modifier.height(AppSpacing.sm))
                    val share = state.dayBlocks.mapValues { (_, m) ->
                        m.toFloat() / totalMinutes.toFloat()
                    }
                    val summary = share.entries
                        .sortedByDescending { it.value }
                        .joinToString("\n") { (b, p) ->
                            "• ${b.displayName()}: ${(p * 100).toInt()}% of your enabled focus time"
                        }
                    Text(
                        text = "$summary\n\nTip: give your hardest work the block where you’re usually freshest.",
                        style = MaterialTheme.typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
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
                enabled = state.dayBlocks.isNotEmpty(),
                modifier = Modifier.weight(1f)
            )
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
    Column(modifier = Modifier.padding(AppSpacing.lg)) {

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {

            Switch(
                checked = enabled,
                onCheckedChange = onToggle
            )

            Spacer(Modifier.width(AppSpacing.md))

            Column {
                Text(
                    text = block.displayName(),
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = block.hint(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        AnimatedVisibility(visible = enabled) {
            Column {
                Spacer(Modifier.height(AppSpacing.sm))
                val options = listOf(30, 60, 90, 120, 180)
                val selected = options.minByOrNull { kotlin.math.abs(it - minutes) } ?: 60
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    options.forEach { option ->
                        AppChip(
                            label = "${option}m",
                            selected = selected == option,
                            onClick = { onMinutesChanged(option) },
                            modifier = Modifier.sizeIn(minWidth = AppSpacing.xxxxxl)
                        )
                    }
                }
                Spacer(Modifier.height(AppSpacing.xs))
                Text(
                    text = "$selected minutes in this block",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

fun DayBlock.displayName(): String =
    when (this) {
        DayBlock.MORNING -> "Morning"
        DayBlock.DAY -> "Afternoon"
        DayBlock.EVENING -> "Evening"
        DayBlock.NIGHT -> "Night"
    }

private fun DayBlock.hint(): String =
    when (this) {
        DayBlock.MORNING -> "Early focus before the day fills up"
        DayBlock.DAY -> "Midday deep work or study windows"
        DayBlock.EVENING -> "Wind-down productivity or hobbies"
        DayBlock.NIGHT -> "Quiet hours for light or creative work"
    }
