package com.habitsheet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitsheet.domain.calculation.DailyShareSummary
import com.habitsheet.resources.*
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

interface ShareService {
    fun shareDailySummary(summary: DailyShareSummary)
}

@Composable
fun ShareImageContent(
    summary: DailyShareSummary,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(360.dp) // Fixed width for consistent image generation
            .background(Color(0xFF020617)) // Match dark aesthetic
            .padding(32.dp),
    ) {
        Text(
            stringResource(Res.string.about_title),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            letterSpacing = 2.sp,
        )

        Text(
            text = formatFullDate(summary.date),
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            modifier = Modifier.padding(top = 4.dp),
        )

        Spacer(Modifier.height(40.dp))

        Text(
            stringResource(Res.string.today_label),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Row(
            modifier = Modifier.padding(top = 8.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                "${summary.completedCount} / ${summary.totalCount}",
                style = MaterialTheme.typography.headlineLarge,
                color = Color.White,
                fontWeight = FontWeight.Bold,
            )
            Text(
                " " + stringResource(Res.string.share_completed),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 8.dp, bottom = 6.dp),
            )
        }

        SubtleProgress(
            progress = summary.percentage,
            modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
        )

        Text(
            text = stringResource(Res.string.share_percent_complete, summary.percentage.percentLabel()),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 8.dp),
        )

        Spacer(Modifier.height(48.dp))

        SectionTitle(stringResource(Res.string.grid_done))
        if (summary.doneHabits.isEmpty()) {
            Text(
                stringResource(Res.string.share_nothing_done),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 12.dp),
            )
        } else if (summary.doneHabits.size == summary.totalCount) {
            Text(
                stringResource(Res.string.share_all_done),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 12.dp),
                fontWeight = FontWeight.Medium,
            )
        } else {
            summary.doneHabits.forEach { habit ->
                ShareHabitRow(habit.name, habit.categoryName, true)
            }
        }

        if (summary.leftHabits.isNotEmpty()) {
            Spacer(Modifier.height(40.dp))
            SectionTitle(stringResource(Res.string.grid_left))
            summary.leftHabits.forEach { habit ->
                ShareHabitRow(habit.name, habit.categoryName, false)
            }
        }

        Spacer(Modifier.height(48.dp))
    }
}

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        letterSpacing = 1.sp,
    )
}

@Composable
private fun ShareHabitRow(name: String, category: String?, completed: Boolean) {
    Column(Modifier.padding(top = 16.dp)) {
        Row(verticalAlignment = Alignment.Top) {
            Text(
                if (completed) "✓" else "□",
                color = if (completed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(end = 12.dp),
            )
            Column {
                Text(
                    name,
                    style = MaterialTheme.typography.bodyLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Medium,
                )
                if (category != null) {
                    Text(
                        category,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 2.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun SharePreviewScreen(
    summary: DailyShareSummary,
    onShare: () -> Unit,
    onClose: () -> Unit,
) {
    val closePreviewLabel = stringResource(Res.string.share_close_preview)
    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    stringResource(Res.string.share_title),
                    style = MaterialTheme.typography.titleLarge,
                )
                IconButton(onClick = onClose) {
                    Text(
                        "✕",
                        fontSize = 20.sp,
                        modifier = Modifier.semantics { contentDescription = closePreviewLabel },
                    )
                }
            }

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .verticalScroll(rememberScrollState()),
                ) {
                    ShareImageContent(summary)
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                OutlinedButton(
                    onClick = onClose,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(stringResource(Res.string.action_cancel))
                }
                Button(
                    onClick = onShare,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(stringResource(Res.string.action_share))
                }
            }
        }
    }
}

private fun formatFullDate(date: LocalDate): String {
    val dayName = date.dayOfWeek.name.lowercase().replaceFirstChar { it.uppercase() }
    val monthName = monthNames[date.month.ordinal]
    return "$dayName, ${date.day} $monthName"
}

private val monthNames = listOf(
    "January", "February", "March", "April", "May", "June",
    "July", "August", "September", "October", "November", "December",
)
