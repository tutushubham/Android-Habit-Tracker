package com.tutushubham.pokidex.feature_onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tutushubham.pokidex.core.domain.model.DayBlock
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.ui.components.AppCard
import com.tutushubham.pokidex.ui.components.AppChip
import com.tutushubham.pokidex.ui.components.PrimaryButton
import com.tutushubham.pokidex.ui.components.SectionHeader
import com.tutushubham.pokidex.ui.theme.AppShapes
import com.tutushubham.pokidex.ui.theme.AppSpacing
import com.tutushubham.pokidex.ui.theme.AppSizes
import kotlin.math.roundToInt

@Composable
fun BlockAssignmentScreen(
    state: OnboardingContract.State,
    onEvent: (OnboardingContract.Event) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val scroll = rememberScrollState()
    val allAssigned = state.dayBlocks.keys.all { state.blockToDomain[it] != null }
    val availableDomains = state.goals.map { it.domain }.toSet().toList()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(horizontal = AppSpacing.lg)
    ) {
        Text(
            text = "← Back",
            style = typography.labelLarge,
            color = scheme.primary,
            modifier = Modifier
                .padding(top = AppSpacing.sm, bottom = AppSpacing.md)
                .clickable(onClick = onBack)
        )

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(scroll)
        ) {
            SectionHeader(
                overline = "Block assignment",
                title = "Design your ideal day rhythm.",
                subtitle = "Map your focus domains to specific time blocks to optimize cognitive performance and reduce decision fatigue."
            )
            Spacer(Modifier.height(AppSpacing.xxl))

            EnergyDistributionChart(
                dayBlocks = state.dayBlocks,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = AppSpacing.md)
            )

            Spacer(Modifier.height(AppSpacing.lg))

            DayBlock.entries.forEach { block ->
                val minutes = state.dayBlocks[block] ?: return@forEach
                val currentDomain = state.blockToDomain[block]
                TimeBlockAssignmentCard(
                    block = block,
                    minutes = minutes,
                    currentDomain = currentDomain,
                    availableDomains = availableDomains,
                    onDomainSelected = { domain ->
                        onEvent(
                            OnboardingContract.Event.BlockDomainAssigned(
                                block = block,
                                domain = domain
                            )
                        )
                    }
                )
                Spacer(Modifier.height(AppSpacing.md))
            }

            Spacer(Modifier.height(AppSpacing.lg))
        }

        PrimaryButton(
            text = "Finalize Schedule",
            onClick = onNext,
            enabled = allAssigned,
            modifier = Modifier.padding(bottom = AppSpacing.sm)
        )
        Text(
            text = "Skip for now",
            style = typography.bodyMedium,
            color = scheme.primary,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onNext)
                .padding(bottom = AppSpacing.lg)
        )
    }
}

@Composable
private fun EnergyDistributionChart(
    dayBlocks: Map<DayBlock, Int>,
    modifier: Modifier = Modifier
) {
    val scheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography
    val ordered = DayBlock.entries.mapNotNull { block ->
        dayBlocks[block]?.let { block to it }
    }
    val totalMinutes = ordered.sumOf { it.second }
    val dayMinutes = 24 * 60
    val centerPercent = if (dayMinutes == 0) {
        0
    } else {
        (totalMinutes * 100f / dayMinutes).roundToInt().coerceIn(0, 100)
    }

    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier.size(200.dp),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val strokeWidth = 28.dp.toPx()
                val radius = size.minDimension / 2f - strokeWidth / 2
                val topLeft = Offset(center.x - radius, center.y - radius)
                val arcSize = Size(radius * 2, radius * 2)
                if (totalMinutes <= 0) {
                    drawArc(
                        color = scheme.surfaceContainerHighest,
                        startAngle = 0f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                } else {
                    var startAngle = -90f
                    ordered.forEach { (block, minutes) ->
                        val sweep = (minutes / totalMinutes.toFloat()) * 360f
                        if (sweep > 0f) {
                            drawArc(
                                color = blockChartColor(block, scheme),
                                startAngle = startAngle,
                                sweepAngle = sweep,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                            )
                            startAngle += sweep
                        }
                    }
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "$centerPercent%",
                    style = typography.headlineMedium.copy(fontWeight = FontWeight.Bold)
                )
                Text(
                    text = "of day",
                    style = typography.bodySmall,
                    color = scheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(AppSpacing.lg))

        Column(
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
            modifier = Modifier.fillMaxWidth()
        ) {
            ordered.forEach { (block, minutes) ->
                val pct = if (totalMinutes == 0) {
                    0
                } else {
                    (minutes * 100f / totalMinutes).roundToInt()
                }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Canvas(Modifier.size(AppSizes.dotSize)) {
                        drawCircle(
                            color = blockChartColor(block, scheme),
                            radius = size.minDimension / 2f
                        )
                    }
                    Spacer(Modifier.width(AppSpacing.sm))
                    Text(
                        text = "${block.displayName()} — $pct%",
                        style = typography.bodyMedium,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "$minutes min",
                        style = typography.bodySmall,
                        color = scheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

private fun blockChartColor(block: DayBlock, scheme: ColorScheme): Color =
    when (block) {
        DayBlock.MORNING -> scheme.primary
        DayBlock.DAY -> scheme.secondary
        DayBlock.EVENING -> scheme.tertiary
        DayBlock.NIGHT -> scheme.primaryContainer
    }

@Composable
private fun TimeBlockAssignmentCard(
    block: DayBlock,
    minutes: Int,
    currentDomain: Domain?,
    availableDomains: List<Domain>,
    onDomainSelected: (Domain) -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val typography = MaterialTheme.typography

    AppCard(
        shape = AppShapes.large,
        containerColor = scheme.surfaceContainerLow
    ) {
            Text(
                text = "${blockEmoji(block)} ${block.displayName()}",
                style = typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
            )
            Spacer(Modifier.height(AppSpacing.xs))
            Text(
                text = blockTimeRange(block),
                style = typography.bodySmall,
                color = scheme.onSurfaceVariant
            )
            Spacer(Modifier.height(AppSpacing.xs))
            Text(
                text = "$minutes min",
                style = typography.labelLarge,
                color = scheme.primary
            )
            Spacer(Modifier.height(AppSpacing.md))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                availableDomains.forEach { domain ->
                    val selected = currentDomain == domain
                    AppChip(
                        label = "${domainEmoji(domain)} ${domain.displayName()}",
                        selected = selected,
                        onClick = { onDomainSelected(domain) },
                        selectedContainerColor = scheme.primary,
                        selectedContentColor = scheme.onPrimary,
                        unselectedContainerColor = scheme.surfaceContainerHighest,
                        unselectedContentColor = scheme.onSurface
                    )
                }
            }
    }
}

private fun blockTimeRange(block: DayBlock): String =
    when (block) {
        DayBlock.MORNING -> "05:00 AM → 12:00 PM"
        DayBlock.DAY -> "12:00 PM → 05:00 PM"
        DayBlock.EVENING -> "05:00 PM → 10:00 PM"
        DayBlock.NIGHT -> "10:00 PM → 05:00 AM"
    }

private fun domainEmoji(domain: Domain): String =
    when (domain) {
        Domain.STUDIES -> "📚"
        Domain.FITNESS -> "💪"
        Domain.WORK -> "💼"
        Domain.HOBBY -> "🎯"
    }

private fun blockEmoji(block: DayBlock): String =
    when (block) {
        DayBlock.MORNING -> "🌅"
        DayBlock.DAY -> "☀️"
        DayBlock.EVENING -> "🌙"
        DayBlock.NIGHT -> "🌃"
    }
