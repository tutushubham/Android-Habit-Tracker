package com.tutushubham.pokidex.feature_goal_detail

import android.widget.Toast
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
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
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.engine.PredictionInsight
import com.tutushubham.pokidex.core.engine.TrendDirection
import com.tutushubham.pokidex.feature_onboarding.displayName
import com.tutushubham.pokidex.ui.components.AppCard
import com.tutushubham.pokidex.ui.components.PrimaryButton
import com.tutushubham.pokidex.ui.components.SectionHeader
import com.tutushubham.pokidex.ui.components.ShimmerLoadingView
import com.tutushubham.pokidex.ui.components.SyncErrorView
import com.tutushubham.pokidex.ui.theme.AppShapes
import com.tutushubham.pokidex.ui.theme.AppSizes
import com.tutushubham.pokidex.ui.theme.AppSpacing
import com.tutushubham.pokidex.ui.theme.PokidexTheme
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
fun GoalDetailScreen(
    viewModel: GoalDetailViewModel,
    onBack: () -> Unit,
    onNavigateToRecommendation: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is GoalDetailContract.GoalDetailEffect.ShowMessage ->
                    Toast.makeText(context, effect.message, Toast.LENGTH_SHORT).show()
                GoalDetailContract.GoalDetailEffect.NavigateBack -> onBack()
            }
        }
    }

    when {
        state.isLoading -> {
            Box(modifier.fillMaxSize()) {
                ShimmerLoadingView(Modifier.fillMaxSize())
            }
        }
        state.error != null -> {
            SyncErrorView(
                subtitle = state.error ?: "",
                onRetry = { viewModel.onEvent(GoalDetailContract.GoalDetailEvent.Refresh) },
                modifier = modifier.fillMaxSize()
            )
        }
        state.insights != null -> {
            GoalDetailContent(
                model = state.insights!!,
                onBack = onBack,
                isEditing = state.isEditing,
                editTitle = state.editTitle,
                editDomain = state.editDomain,
                editTarget = state.editTarget,
                editDeadline = state.editDeadline,
                onEvent = viewModel::onEvent,
                onNavigateToRecommendation = onNavigateToRecommendation,
                modifier = modifier
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalDetailContent(
    model: GoalInsightsUiModel,
    onBack: () -> Unit,
    isEditing: Boolean = false,
    editTitle: String = "",
    editDomain: Domain = Domain.STUDIES,
    editTarget: Int = 0,
    editDeadline: LocalDate = LocalDate.now(),
    onEvent: (GoalDetailContract.GoalDetailEvent) -> Unit = {},
    onNavigateToRecommendation: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        TopBar(
            title = model.goalTitle,
            onBack = onBack,
            onToggleEdit = { onEvent(GoalDetailContract.GoalDetailEvent.ToggleEditMode) },
            isEditing = isEditing
        )

        Spacer(Modifier.height(AppSpacing.sm))

        EditorialHeader(model = model)

        Spacer(Modifier.height(AppSpacing.lg))

        if (!isEditing) {
            PrimaryButton(
                text = "Initiate Blueprint",
                onClick = { onEvent(GoalDetailContract.GoalDetailEvent.ToggleEditMode) },
                modifier = Modifier.padding(horizontal = AppSpacing.xl)
            )
            Spacer(Modifier.height(AppSpacing.lg))
        }

        if (isEditing) {
            GoalEditFormSection(
                editTitle = editTitle,
                editDomain = editDomain,
                editTarget = editTarget,
                editDeadline = editDeadline,
                onEvent = onEvent
            )
            Spacer(Modifier.height(AppSpacing.md))
            PrimaryButton(
                text = "Save Changes",
                onClick = { onEvent(GoalDetailContract.GoalDetailEvent.SaveChanges) },
                modifier = Modifier.padding(horizontal = AppSpacing.xl)
            )
            Spacer(Modifier.height(AppSpacing.xxl))
        }

        Spacer(Modifier.height(AppSpacing.md))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.xl),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            ProgressTrendCard(
                weeklyActivity = model.velocity.weeklyActivity,
                modifier = Modifier.weight(1.6f)
            )
            StreakCard(
                streakDays = model.streakDays,
                streakLabel = model.streakLabel,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(Modifier.height(AppSpacing.md))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = AppSpacing.xl),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            VelocityCard(velocity = model.velocity, prediction = model.prediction, modifier = Modifier.weight(1f))
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
            ) {
                FatigueCard(fatigue = model.fatigue)
                DurationCard(duration = model.duration)
            }
        }

        Spacer(Modifier.height(AppSpacing.xxxl))

        RecommendationSection(
            recommendations = model.recommendations,
            onNavigateToRecommendation = onNavigateToRecommendation
        )

        Spacer(Modifier.height(AppSpacing.xxxl))

        RecentUnitsSection(sessions = model.recentSessions)

        Spacer(Modifier.height(AppSpacing.xxxxl))
    }
}

@Composable
private fun TopBar(
    title: String,
    onBack: () -> Unit,
    onToggleEdit: () -> Unit,
    isEditing: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "Back"
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f),
            maxLines = 1
        )
        IconButton(onClick = onToggleEdit) {
            Icon(
                imageVector = if (isEditing) Icons.Filled.Close else Icons.Filled.Edit,
                contentDescription = if (isEditing) "Close editor" else "Edit goal"
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalEditFormSection(
    editTitle: String,
    editDomain: Domain,
    editTarget: Int,
    editDeadline: LocalDate,
    onEvent: (GoalDetailContract.GoalDetailEvent) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(horizontal = AppSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
    ) {
        SectionHeader(overline = "BLUEPRINT", title = "Edit Goal")

        OutlinedTextField(
            value = editTitle,
            onValueChange = { onEvent(GoalDetailContract.GoalDetailEvent.UpdateTitle(it)) },
            label = { Text("Title") },
            modifier = Modifier.fillMaxWidth(),
            shape = AppShapes.medium,
            singleLine = true
        )
        DomainDropdown(
            selected = editDomain,
            onSelect = { onEvent(GoalDetailContract.GoalDetailEvent.UpdateDomain(it)) }
        )
        TargetNumberField(
            value = editTarget,
            onValueChange = { onEvent(GoalDetailContract.GoalDetailEvent.UpdateTarget(it)) }
        )
        GoalDeadlinePicker(
            deadline = editDeadline,
            onDeadlineChange = { onEvent(GoalDetailContract.GoalDetailEvent.UpdateDeadline(it)) }
        )
    }
}

@Composable
private fun DomainDropdown(
    selected: Domain,
    onSelect: (Domain) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text("Domain", style = MaterialTheme.typography.labelMedium)
        Spacer(Modifier.height(AppSpacing.xs))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            Domain.entries.forEach { domain ->
                FilterChip(
                    selected = selected == domain,
                    onClick = { onSelect(domain) },
                    label = { Text(domain.displayName()) }
                )
            }
        }
    }
}

@Composable
private fun TargetNumberField(
    value: Int,
    onValueChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = if (value == 0) "" else value.toString(),
        onValueChange = { raw ->
            val digits = raw.filter { it.isDigit() }
            val n = digits.toIntOrNull() ?: 0
            onValueChange(n)
        },
        label = { Text("Target (units)") },
        modifier = modifier.fillMaxWidth(),
        shape = AppShapes.medium,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GoalDeadlinePicker(
    deadline: LocalDate,
    onDeadlineChange: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    var showDatePicker by remember { mutableStateOf(false) }
    TextButton(
        onClick = { showDatePicker = true },
        modifier = modifier.fillMaxWidth()
    ) {
        Text("Deadline: $deadline")
    }
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = deadline
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
                            onDeadlineChange(date)
                        }
                        showDatePicker = false
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Cancel")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }
}

@Composable
private fun EditorialHeader(
    model: GoalInsightsUiModel,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.xl),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Column(modifier = Modifier.weight(1f)) {
            SectionHeader(overline = "CURRENT MILESTONE", title = model.milestoneName)
            Spacer(Modifier.height(AppSpacing.sm))
            Text(
                text = "You are ${model.progressLabel} through your ${model.goalTitle} goal. ${model.daysRemaining} days remaining.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 22.sp
            )
        }

        Spacer(Modifier.width(AppSpacing.lg))

        ProgressGauge(fraction = model.progressFraction, label = model.progressLabel)
    }
}

@Composable
private fun ProgressGauge(
    fraction: Float,
    label: String,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val track = MaterialTheme.colorScheme.surfaceContainerHighest

    Box(
        modifier = modifier.size(100.dp),
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
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun ProgressTrendCard(
    weeklyActivity: List<Float>,
    modifier: Modifier = Modifier
) {
    AppCard(modifier = modifier, shape = AppShapes.extraLarge, containerColor = MaterialTheme.colorScheme.surface) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Progress Trend",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                LegendDot("Actual", MaterialTheme.colorScheme.primary)
                LegendDot("Required", MaterialTheme.colorScheme.outlineVariant)
            }
        }

        Spacer(Modifier.height(AppSpacing.lg))

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(100.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.Bottom
        ) {
            val days = listOf("M", "T", "W", "T", "F", "S", "S")
            weeklyActivity.forEachIndexed { i, frac ->
                MiniBar(
                    fraction = frac.coerceIn(0.05f, 1f),
                    label = days.getOrElse(i) { "" },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun LegendDot(
    label: String,
    color: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier
                .size(6.dp)
                .clip(CircleShape)
                .background(color)
        )
        Spacer(Modifier.width(AppSpacing.xs))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 9.sp
        )
    }
}

@Composable
private fun MiniBar(
    fraction: Float,
    label: String,
    modifier: Modifier = Modifier
) {
    val barHeight = 80.dp
    Column(
        modifier = modifier.padding(horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(barHeight)
                .clip(AppShapes.small)
                .background(MaterialTheme.colorScheme.surfaceContainerLow),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(barHeight * fraction)
                    .clip(AppShapes.small)
                    .background(
                        MaterialTheme.colorScheme.primary.copy(
                            alpha = (0.3f + fraction * 0.7f).coerceIn(0.2f, 1f)
                        )
                    )
            )
        }
        Spacer(Modifier.height(AppSpacing.xs))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 8.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun StreakCard(
    streakDays: Int,
    streakLabel: String,
    modifier: Modifier = Modifier
) {
    val primary = MaterialTheme.colorScheme.primary
    val container = MaterialTheme.colorScheme.primaryContainer

    Surface(
        modifier = modifier,
        shape = AppShapes.extraLarge,
        color = primary
    ) {
        Box(
            modifier = Modifier
                .background(
                    Brush.linearGradient(listOf(primary, container.copy(alpha = 0.8f)))
                )
                .padding(AppSpacing.xl)
        ) {
            Column {
                Text(text = "🔥", fontSize = 28.sp)
                Spacer(Modifier.height(AppSpacing.sm))
                Text(
                    "Daily Streak",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(AppSpacing.sm))
                Text(
                    "$streakDays",
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontWeight = FontWeight.ExtraBold,
                    letterSpacing = (-2).sp
                )
                Text(
                    streakLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimary.copy(alpha = 0.8f),
                    fontSize = 10.sp
                )
            }
        }
    }
}

@Composable
private fun VelocityCard(
    velocity: VelocityInsight,
    prediction: PredictionInsight,
    modifier: Modifier = Modifier
) {
    AppCard(modifier = modifier, shape = AppShapes.extraLarge, containerColor = MaterialTheme.colorScheme.surface,
        contentPadding = Modifier.padding(AppSpacing.xl)) {
        Text(
            "Velocity Analysis",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )

        Spacer(Modifier.height(AppSpacing.lg))

        PaceRow(
            label = "Actual Pace",
            value = "%.1f units/day".format(velocity.actualPace),
            fraction = if (velocity.requiredPace > 0)
                (velocity.actualPace / velocity.requiredPace).toFloat().coerceIn(0f, 1f) else 0f,
            color = MaterialTheme.colorScheme.primary,
            isBold = true
        )

        Spacer(Modifier.height(AppSpacing.md))

        PaceRow(
            label = "Required Pace",
            value = "%.1f units/day".format(velocity.requiredPace),
            fraction = if (velocity.actualPace > 0)
                (velocity.requiredPace / velocity.actualPace).toFloat().coerceIn(0f, 1f) else 0f,
            color = MaterialTheme.colorScheme.outlineVariant,
            isBold = false
        )

        Spacer(Modifier.height(AppSpacing.lg))

        val trendArrow = when (velocity.trend) {
            TrendDirection.UP -> "↑"
            TrendDirection.DOWN -> "↓"
            TrendDirection.FLAT -> "→"
        }
        val aheadText = when {
            velocity.percentAhead > 0 -> "${trendArrow} ${velocity.percentAhead}% ahead of schedule"
            velocity.percentAhead < 0 -> "${trendArrow} ${-velocity.percentAhead}% behind schedule"
            else -> "${trendArrow} On pace"
        }

        val predictedText = prediction.predictedDate?.let {
            ". Predicted: ${it.format(DateTimeFormatter.ofPattern("MMM d"))}"
        } ?: ""

        Surface(
            shape = AppShapes.medium,
            color = if (velocity.percentAhead >= 0)
                MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        ) {
            Text(
                text = "$aheadText$predictedText",
                modifier = Modifier.padding(AppSpacing.md),
                style = MaterialTheme.typography.bodySmall,
                color = if (velocity.percentAhead >= 0)
                    MaterialTheme.colorScheme.onTertiaryContainer
                else
                    MaterialTheme.colorScheme.onErrorContainer,
                fontWeight = FontWeight.Medium,
                fontSize = 11.sp,
                lineHeight = 16.sp
            )
        }
    }
}

@Composable
private fun PaceRow(
    label: String,
    value: String,
    fraction: Float,
    color: androidx.compose.ui.graphics.Color,
    isBold: Boolean,
    modifier: Modifier = Modifier
) {
    Column(modifier) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
                color = if (isBold) color else MaterialTheme.colorScheme.onSurface
            )
        }
        Spacer(Modifier.height(AppSpacing.xs))
        LinearProgressIndicator(
            progress = { fraction },
            modifier = Modifier.fillMaxWidth().height(6.dp),
            color = color,
            trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        )
    }
}

@Composable
private fun FatigueCard(
    fatigue: FatigueInsight,
    modifier: Modifier = Modifier
) {
    val levelColor = when (fatigue.levelLabel) {
        "Low" -> MaterialTheme.colorScheme.tertiary
        "Medium" -> MaterialTheme.colorScheme.secondary
        else -> MaterialTheme.colorScheme.error
    }
    val levelEmoji = when (fatigue.levelLabel) {
        "Low" -> "😊"
        "Medium" -> "😐"
        else -> "😰"
    }

    AppCard(modifier = modifier, shape = AppShapes.extraLarge, containerColor = MaterialTheme.colorScheme.surface) {
        Text(
            "FATIGUE INDEX",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            fontSize = 9.sp
        )

        Spacer(Modifier.height(AppSpacing.sm))

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = levelEmoji, fontSize = 24.sp)
            Spacer(Modifier.width(AppSpacing.sm))
            Column {
                Text(
                    fatigue.levelLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = levelColor,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    if (fatigue.skipStreak > 0) "${fatigue.skipStreak} recent skips"
                    else "No skip patterns",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp
                )
            }
        }

        Spacer(Modifier.height(AppSpacing.sm))

        Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
            repeat(5) { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(AppSpacing.xs)
                        .clip(AppShapes.small)
                        .background(
                            if (i < fatigue.segmentsFilled) levelColor
                            else MaterialTheme.colorScheme.surfaceContainerHighest
                        )
                )
            }
        }
    }
}

@Composable
private fun DurationCard(
    duration: DurationInsight,
    modifier: Modifier = Modifier
) {
    AppCard(modifier = modifier, shape = AppShapes.extraLarge, containerColor = MaterialTheme.colorScheme.surface) {
        Text(
            "UNIT DURATION",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp,
            fontSize = 9.sp
        )

        Spacer(Modifier.height(AppSpacing.sm))

        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                "${duration.averageMinutes}",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold
            )
            Text(
                "min",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(bottom = AppSpacing.xs, start = 2.dp)
            )

            Spacer(Modifier.width(AppSpacing.sm))

            val trendIcon = when (duration.trend) {
                TrendDirection.UP -> "↑"
                TrendDirection.DOWN -> "↓"
                TrendDirection.FLAT -> "→"
            }
            Text(
                trendIcon,
                style = MaterialTheme.typography.titleMedium,
                color = when (duration.trend) {
                    TrendDirection.DOWN -> MaterialTheme.colorScheme.tertiary
                    TrendDirection.UP -> MaterialTheme.colorScheme.error
                    TrendDirection.FLAT -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.padding(bottom = AppSpacing.xs)
            )
        }

        Text(
            duration.trendLabel,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 10.sp
        )
    }
}

@Composable
private fun RecommendationSection(
    recommendations: List<Recommendation>,
    onNavigateToRecommendation: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Column(modifier.padding(horizontal = AppSpacing.xl)) {
        SectionHeader(overline = "INTELLIGENCE", title = "Recommendations")

        Spacer(Modifier.height(AppSpacing.lg))

        recommendations.forEach { rec ->
            RecommendationCard(rec)
            Spacer(Modifier.height(AppSpacing.md))
        }

        Spacer(Modifier.height(AppSpacing.sm))

        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            PrimaryButton(
                text = "Adjust Goal",
                onClick = { },
                modifier = Modifier.weight(1f)
            )
            FilledTonalButton(
                onClick = onNavigateToRecommendation,
                modifier = Modifier
                    .weight(1f)
                    .height(AppSizes.buttonHeight),
                shape = AppShapes.pill,
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Text("Rebalance Plan", fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun RecommendationCard(
    rec: Recommendation,
    modifier: Modifier = Modifier
) {
    val borderColor = when (rec.type) {
        RecommendationType.RECOVERY -> MaterialTheme.colorScheme.error
        RecommendationType.WARNING -> MaterialTheme.colorScheme.error.copy(alpha = 0.6f)
        RecommendationType.SCHEDULE -> MaterialTheme.colorScheme.primary
        RecommendationType.STRETCH -> MaterialTheme.colorScheme.tertiary
    }

    AppCard(modifier = modifier, shape = AppShapes.large, containerColor = MaterialTheme.colorScheme.surface) {
        Row(verticalAlignment = Alignment.Top) {
            Box(
                modifier = Modifier
                    .width(AppSpacing.xs)
                    .height(AppSpacing.xxxxl)
                    .clip(AppShapes.small)
                    .background(borderColor)
            )
            Spacer(Modifier.width(AppSpacing.md))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(rec.icon, fontSize = 16.sp)
                    Spacer(Modifier.width(AppSpacing.sm))
                    Text(
                        rec.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
                Spacer(Modifier.height(AppSpacing.xs))
                Text(
                    rec.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp,
                    fontSize = 11.sp
                )
            }
        }
    }
}

@Composable
private fun RecentUnitsSection(
    sessions: List<RecentSessionUi>,
    modifier: Modifier = Modifier
) {
    if (sessions.isEmpty()) return

    Column(modifier.padding(horizontal = AppSpacing.xl)) {
        Text(
            "Recent Units",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold
        )

        Spacer(Modifier.height(AppSpacing.md))

        sessions.forEach { session ->
            RecentUnitRow(session)
            Spacer(Modifier.height(AppSpacing.sm))
        }
    }
}

@Composable
private fun RecentUnitRow(
    session: RecentSessionUi,
    modifier: Modifier = Modifier
) {
    AppCard(modifier = modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(Modifier.weight(1f)) {
                Text(
                    session.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "${session.date.format(DateTimeFormatter.ofPattern("MMM d"))} • ${session.durationMinutes} mins",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Surface(
                shape = AppShapes.extraLarge,
                color = MaterialTheme.colorScheme.tertiaryContainer
            ) {
                Text(
                    session.badge,
                    modifier = Modifier.padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp
                )
            }
        }
    }
}

private val previewModel = GoalInsightsUiModel(
    goalTitle = "DSA Mastery",
    domain = Domain.STUDIES,
    milestoneName = "Graphs & Trees",
    progressFraction = 0.68f,
    progressLabel = "68%",
    daysRemaining = 42,
    velocity = VelocityInsight(
        actualPace = 4.2,
        requiredPace = 3.8,
        trend = TrendDirection.UP,
        percentAhead = 12,
        weeklyActivity = listOf(0.4f, 0.55f, 0.45f, 0.75f, 0.9f, 0.1f, 0.1f)
    ),
    fatigue = FatigueInsight(
        levelLabel = "Low",
        skipRate = 0.05,
        skipStreak = 0,
        segmentsFilled = 1
    ),
    prediction = PredictionInsight(
        predictedDate = LocalDate.of(2026, 10, 24),
        confidence = 0.82,
        confidenceLabel = "High confidence"
    ),
    duration = DurationInsight(
        averageMinutes = 35,
        staticMinutes = 30,
        trend = TrendDirection.FLAT,
        trendLabel = "Consistent pace"
    ),
    recommendations = listOf(
        Recommendation(
            title = "Stretch Opportunity",
            message = "You're 12% ahead. Consider increasing session depth or adding advanced topics.",
            icon = "🚀",
            type = RecommendationType.STRETCH
        ),
        Recommendation(
            title = "Sessions Taking Longer",
            message = "Average duration (35m) exceeds your estimate (30m). Consider splitting into smaller units.",
            icon = "⏱️",
            type = RecommendationType.WARNING
        )
    ),
    streakDays = 12,
    streakLabel = "Days of consistent study",
    recentSessions = listOf(
        RecentSessionUi("Binary Search Trees", LocalDate.of(2026, 3, 25), 42, "Completed"),
        RecentSessionUi("AVL Tree Rotations", LocalDate.of(2026, 3, 24), 28, "Completed")
    )
)

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun GoalDetailScreenPreview() {
    PokidexTheme {
        GoalDetailContent(model = previewModel, onBack = {})
    }
}

@Preview(showBackground = true)
@Composable
private fun VelocityCardPreview() {
    PokidexTheme {
        VelocityCard(
            velocity = previewModel.velocity,
            prediction = previewModel.prediction,
            modifier = Modifier.padding(AppSpacing.xl)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FatigueCardPreview() {
    PokidexTheme {
        FatigueCard(
            fatigue = FatigueInsight("High", 0.65, 3, 5),
            modifier = Modifier.padding(AppSpacing.xl)
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun RecommendationSectionPreview() {
    PokidexTheme {
        RecommendationSection(recommendations = previewModel.recommendations)
    }
}
