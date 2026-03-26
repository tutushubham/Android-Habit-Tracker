package com.tutushubham.pokidex.feature_insights

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.ui.components.AppCard
import com.tutushubham.pokidex.ui.components.MetricCard
import com.tutushubham.pokidex.ui.components.SectionHeader
import com.tutushubham.pokidex.ui.components.ShimmerLoadingView
import com.tutushubham.pokidex.ui.components.SyncErrorView
import com.tutushubham.pokidex.ui.theme.AppShapes
import com.tutushubham.pokidex.ui.theme.AppSizes
import com.tutushubham.pokidex.ui.theme.AppSpacing
import com.tutushubham.pokidex.ui.theme.PokidexTheme
import java.time.DayOfWeek
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun InsightsScreen(
    viewModel: InsightsViewModel,
    onNavigateToFatigue: () -> Unit = {},
    onNavigateToMomentum: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    when {
        state.isLoading -> {
            ShimmerLoadingView(modifier.fillMaxSize())
        }
        state.error != null -> {
            SyncErrorView(onRetry = { viewModel.onEvent(InsightsContract.InsightsEvent.Refresh) })
        }
        state.insights != null -> {
            InsightsContent(
                model = state.insights!!,
                onNavigateToFatigue = onNavigateToFatigue,
                onNavigateToMomentum = onNavigateToMomentum,
                modifier = modifier
            )
        }
    }
}

@Composable
private fun InsightsContent(
    model: InsightsUiModel,
    onNavigateToFatigue: () -> Unit = {},
    onNavigateToMomentum: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        InsightsHeader()

        Spacer(Modifier.height(AppSpacing.lg))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.xl),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            MetricCard(
                title = "This Week",
                value = "${model.totalMinutesThisWeek / 60}h ${model.totalMinutesThisWeek % 60}m",
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "Sessions",
                value = "${model.totalSessionsThisWeek}",
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(AppSpacing.lg))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.xl),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            AiHeroCard(
                model = model,
                modifier = Modifier
                    .weight(2f)
                    .clickable { onNavigateToFatigue() }
            )
            StreakCircle(
                streakDays = model.streakDays,
                personalBest = model.personalBestStreak,
                modifier = Modifier
                    .weight(1f)
                    .clickable { onNavigateToMomentum() }
            )
        }

        Spacer(Modifier.height(AppSpacing.xxxl))

        WeeklyOverview(activity = model.weeklyActivity)

        Spacer(Modifier.height(AppSpacing.xxxl))

        CompletionRates(completions = model.completionRates)

        Spacer(Modifier.height(AppSpacing.xxxl))

        HabitArchetypeSection(
            primary = model.habitArchetype,
            secondary = model.secondaryArchetype
        )

        Spacer(Modifier.height(AppSpacing.xxxxl))
    }
}

@Composable
private fun InsightsHeader(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.xl, vertical = AppSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "Insights",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
private fun AiHeroCard(
    model: InsightsUiModel,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val primaryDim = MaterialTheme.colorScheme.primaryContainer

    Surface(
        modifier = modifier,
        shape = AppShapes.extraLarge,
        shadowElevation = 4.dp,
        color = MaterialTheme.colorScheme.primary
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.linearGradient(
                        colors = listOf(primary, primary.copy(alpha = 0.85f), primaryDim.copy(alpha = 0.6f))
                    )
                )
                .padding(AppSpacing.xl)
        ) {
            Column {
                Text(
                    text = "AI MINDSET ANALYSIS",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.7f),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                    fontSize = 9.sp
                )

                Spacer(Modifier.height(AppSpacing.md))

                Text(
                    text = "Focus peaks between ${formatHour(model.peakFocusTime.first)} and ${formatHour(model.peakFocusTime.second)}.",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 24.sp
                )

                Spacer(Modifier.height(AppSpacing.sm))

                Text(
                    text = model.summaryInsight,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.85f),
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Composable
private fun StreakCircle(
    streakDays: Int,
    personalBest: Int,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val fraction = if (personalBest > 0) (streakDays.toFloat() / personalBest).coerceIn(0f, 1f) else 0f

    Surface(
        modifier = modifier,
        shape = AppShapes.extraLarge,
        color = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier.padding(AppSpacing.lg),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize().padding(AppSpacing.xs)) {
                    val strokeWidth = 10.dp.toPx()
                    val arcSize = Size(size.width - strokeWidth, size.height - strokeWidth)
                    val topLeft = Offset(strokeWidth / 2, strokeWidth / 2)

                    drawArc(
                        color = track, startAngle = -90f, sweepAngle = 360f,
                        useCenter = false, topLeft = topLeft, size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                    drawArc(
                        color = primary, startAngle = -90f, sweepAngle = 360f * fraction,
                        useCenter = false, topLeft = topLeft, size = arcSize,
                        style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                    )
                }

                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "$streakDays",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = "DAY STREAK",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.Bold,
                        fontSize = 7.sp,
                        letterSpacing = 1.sp
                    )
                }
            }

            Spacer(Modifier.height(AppSpacing.sm))

            Text(
                text = "Keep the momentum",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )

            val gap = (personalBest - streakDays).coerceAtLeast(0)
            if (gap > 0) {
                Text(
                    text = "$gap days from your record.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    textAlign = TextAlign.Center
                )
            } else {
                Text(
                    text = "New personal best!",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun WeeklyOverview(
    activity: List<DayActivity>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(horizontal = AppSpacing.xl)) {
        SectionHeader(overline = "ACTIVITY MAP", title = "Weekly Overview")

        Spacer(Modifier.height(AppSpacing.lg))

        AppCard(shape = AppShapes.extraLarge, containerColor = MaterialTheme.colorScheme.surface) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.xl),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                activity.forEach { day ->
                    DayBar(day = day)
                }
            }
        }
    }
}

@Composable
private fun DayBar(
    day: DayActivity,
    modifier: Modifier = Modifier
) {
    val barHeight = 120.dp
    val fillFraction = day.fractionOfMax.coerceIn(0.05f, 1f)
    val isToday = day.dayOfWeek == java.time.LocalDate.now().dayOfWeek

    Column(
        modifier = modifier.width(36.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (day.totalMinutes > 0) {
            Text(
                text = "${day.totalMinutes}m",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 8.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(AppSpacing.xs))
        } else {
            Spacer(Modifier.height(AppSpacing.lg))
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .clip(AppShapes.medium)
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight * fillFraction)
                    .clip(AppShapes.medium)
                    .background(
                        if (isToday) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    )
            )
        }

        Spacer(Modifier.height(AppSpacing.sm))

        Text(
            text = day.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()),
            style = MaterialTheme.typography.labelSmall,
            color = if (isToday) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun CompletionRates(
    completions: List<GoalCompletion>,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(horizontal = AppSpacing.xl)) {
        SectionHeader(overline = "GOAL MASTERY", title = "Completion Rates")

        Spacer(Modifier.height(AppSpacing.lg))

        completions.forEach { goal ->
            CompletionCard(goal = goal)
            Spacer(Modifier.height(AppSpacing.md))
        }
    }
}

@Composable
private fun CompletionCard(
    goal: GoalCompletion,
    modifier: Modifier = Modifier
) {
    val progressColor = when {
        goal.completionRate >= 0.7f -> MaterialTheme.colorScheme.primary
        goal.completionRate >= 0.4f -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.error
    }

    val trendBgColor = when (goal.trend) {
        CompletionTrend.UP -> MaterialTheme.colorScheme.tertiaryContainer
        CompletionTrend.STEADY -> MaterialTheme.colorScheme.surfaceContainerHigh
        CompletionTrend.DOWN -> MaterialTheme.colorScheme.errorContainer
    }
    val trendTextColor = when (goal.trend) {
        CompletionTrend.UP -> MaterialTheme.colorScheme.onTertiaryContainer
        CompletionTrend.STEADY -> MaterialTheme.colorScheme.onSurfaceVariant
        CompletionTrend.DOWN -> MaterialTheme.colorScheme.onErrorContainer
    }

    AppCard(
        modifier = modifier,
        shape = AppShapes.extraLarge,
        containerColor = MaterialTheme.colorScheme.surface,
        contentPadding = Modifier.padding(AppSpacing.xl)
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    modifier = Modifier.size(44.dp),
                    shape = AppShapes.medium,
                    color = progressColor.copy(alpha = 0.1f)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text(
                            text = domainEmoji(goal.domain),
                            fontSize = 20.sp,
                            modifier = Modifier.padding(AppSpacing.sm)
                        )
                    }
                }
                Spacer(Modifier.width(AppSpacing.md))
                Text(
                    text = goal.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }

            Surface(
                shape = AppShapes.extraLarge,
                color = trendBgColor
            ) {
                Text(
                    text = goal.trendLabel,
                    modifier = Modifier.padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs),
                    style = MaterialTheme.typography.labelSmall,
                    color = trendTextColor,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }
        }

        Spacer(Modifier.height(AppSpacing.lg))

        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            LinearProgressIndicator(
                progress = { goal.completionRate.coerceIn(0f, 1f) },
                modifier = Modifier.weight(1f).height(AppSizes.progressBarHeight),
                color = progressColor,
                trackColor = progressColor.copy(alpha = 0.12f),
            )
            Text(
                text = "${(goal.completionRate * 100).toInt()}%",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HabitArchetypeSection(
    primary: HabitType,
    secondary: HabitType?,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.padding(start = AppSpacing.xl)) {
        Text(
            text = "Habit Archetype",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold,
            modifier = Modifier.padding(end = AppSpacing.xl)
        )

        Spacer(Modifier.height(AppSpacing.lg))

        val archetypes = listOfNotNull(primary, secondary)

        LazyRow(
            contentPadding = PaddingValues(end = AppSpacing.xl),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            items(archetypes) { archetype ->
                ArchetypeCard(archetype = archetype, isPrimary = archetype == primary)
            }
        }
    }
}

@Composable
private fun ArchetypeCard(
    archetype: HabitType,
    isPrimary: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.width(260.dp),
        shape = AppShapes.extraLarge,
        color = if (isPrimary)
            MaterialTheme.colorScheme.surfaceContainerLow
        else
            MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Column(Modifier.padding(AppSpacing.xxl)) {
            Text(text = archetype.icon, fontSize = 32.sp)

            Spacer(Modifier.height(AppSpacing.md))

            Text(
                text = archetype.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            Spacer(Modifier.height(AppSpacing.sm))

            Text(
                text = archetype.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )
        }
    }
}

private fun domainEmoji(domain: Domain): String = when (domain) {
    Domain.STUDIES -> "📚"
    Domain.FITNESS -> "💪"
    Domain.WORK -> "💼"
    Domain.HOBBY -> "🎯"
}

private fun formatHour(hour: Int): String = when {
    hour == 0 -> "12 AM"
    hour < 12 -> "$hour AM"
    hour == 12 -> "12 PM"
    else -> "${hour - 12} PM"
}

private val previewModel = InsightsUiModel(
    peakFocusTime = 9 to 11,
    streakDays = 12,
    personalBestStreak = 15,
    weeklyActivity = listOf(
        DayActivity(DayOfWeek.MONDAY, 60, 0.6f),
        DayActivity(DayOfWeek.TUESDAY, 40, 0.4f),
        DayActivity(DayOfWeek.WEDNESDAY, 100, 1.0f),
        DayActivity(DayOfWeek.THURSDAY, 55, 0.55f),
        DayActivity(DayOfWeek.FRIDAY, 70, 0.7f),
        DayActivity(DayOfWeek.SATURDAY, 20, 0.2f),
        DayActivity(DayOfWeek.SUNDAY, 15, 0.15f)
    ),
    completionRates = listOf(
        GoalCompletion("i1", "DSA Mastery", Domain.STUDIES, 0.75f, CompletionTrend.UP, "+12%"),
        GoalCompletion("i2", "Morning Exercise", Domain.FITNESS, 0.92f, CompletionTrend.STEADY, "Steady"),
        GoalCompletion("i3", "App Development", Domain.WORK, 0.38f, CompletionTrend.DOWN, "-5%")
    ),
    habitArchetype = HabitType.EARLY_BIRD,
    secondaryArchetype = HabitType.SPRINT_FINISHER,
    summaryInsight = "Your consistency in deep work sessions has increased by 14% this week. You're most likely to complete high-complexity tasks when scheduled in the morning.",
    totalMinutesThisWeek = 360,
    totalSessionsThisWeek = 14
)

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun InsightsScreenPreview() {
    PokidexTheme {
        InsightsContent(model = previewModel)
    }
}

@Preview(showBackground = true)
@Composable
private fun AiHeroCardPreview() {
    PokidexTheme {
        AiHeroCard(model = previewModel, modifier = Modifier.padding(AppSpacing.xl))
    }
}

@Preview(showBackground = true)
@Composable
private fun StreakCirclePreview() {
    PokidexTheme {
        StreakCircle(streakDays = 12, personalBest = 15, modifier = Modifier.padding(AppSpacing.xl))
    }
}

@Preview(showBackground = true)
@Composable
private fun WeeklyOverviewPreview() {
    PokidexTheme {
        WeeklyOverview(activity = previewModel.weeklyActivity)
    }
}

@Preview(showBackground = true)
@Composable
private fun CompletionRatesPreview() {
    PokidexTheme {
        CompletionRates(completions = previewModel.completionRates)
    }
}

@Preview(showBackground = true)
@Composable
private fun HabitArchetypePreview() {
    PokidexTheme {
        HabitArchetypeSection(primary = HabitType.EARLY_BIRD, secondary = HabitType.SPRINT_FINISHER)
    }
}
