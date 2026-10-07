package com.habitsheet.ui.month

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.habitsheet.presentation.MonthUiState
import com.habitsheet.resources.*
import com.habitsheet.ui.HabitIcon
import com.habitsheet.ui.HabitIconGlyph
import com.habitsheet.ui.HabitSheetTheme
import com.habitsheet.ui.monthName
import com.habitsheet.ui.tutorialTarget
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun MonthTopBar(
    state: MonthUiState,
    actions: MonthActions,
    compact: Boolean,
    onShare: () -> Unit = {},
    onSettings: () -> Unit = {},
    onPosition: (String, Rect) -> Unit = { _, _ -> },
    onShowPicker: () -> Unit = {},
) {
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    Column(
        Modifier
            .fillMaxWidth()
            .then(if (compact) Modifier.windowInsetsPadding(WindowInsets.statusBars) else Modifier)
            .height(if (compact) 126.dp else 112.dp)
            .drawBehind {
                drawLine(borderColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            }
            .padding(horizontal = if (compact) 12.dp else 24.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
    ) {
        Row(
            Modifier.fillMaxWidth().height(52.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (compact) {
                val settingsLabel = stringResource(Res.string.nav_settings)
                IconButton(
                    onClick = onSettings,
                    modifier = Modifier
                        .sizeIn(minWidth = 48.dp, minHeight = 48.dp)
                        .semantics { contentDescription = settingsLabel },
                ) {
                    HabitIcon(
                        HabitIconGlyph.Settings,
                        Modifier.size(22.dp),
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val pickerDescription = stringResource(Res.string.month_picker_description, state.selectedMonth.monthName(), state.selectedMonth.year)
            Column(
                Modifier
                    .weight(1f)
                    .then(
                        if (state.todayMode) {
                            Modifier
                        } else {
                            Modifier
                                .clickable(onClick = onShowPicker)
                                .semantics(mergeDescendants = true) {
                                    role = Role.Button
                                    contentDescription = pickerDescription
                                }
                        },
                    ),
                horizontalAlignment = Alignment.Start,
            ) {
                Text(
                    text = if (state.todayMode) {
                        stringResource(Res.string.title_day_plan)
                    } else {
                        stringResource(Res.string.month_title, state.selectedMonth.monthName(), state.selectedMonth.year)
                    },
                    style = if (compact) MaterialTheme.typography.titleLarge else MaterialTheme.typography.headlineMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (state.todayMode) {
                TextButton(
                    onClick = onShare,
                    modifier = Modifier
                        .sizeIn(minWidth = 56.dp, minHeight = 48.dp)
                        .tutorialTarget("share", onPosition),
                ) {
                    Text(stringResource(Res.string.share_day), color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            DateNavigationButton(
                previous = true,
                dayMode = state.todayMode,
                compact = compact,
                onClick = if (state.todayMode) actions.previousDay else actions.previousMonth,
            )
            ModeSwitcher(
                todayMode = state.todayMode,
                onModeChange = actions.setTodayMode,
                compact = compact,
            )
            DateNavigationButton(
                previous = false,
                dayMode = state.todayMode,
                compact = compact,
                onClick = if (state.todayMode) actions.nextDay else actions.nextMonth,
            )
        }
    }
}

@Composable
private fun DateNavigationButton(previous: Boolean, dayMode: Boolean, compact: Boolean, onClick: () -> Unit) {
    val description = stringResource(
        when {
            previous && dayMode -> Res.string.date_prev_day
            previous -> Res.string.date_prev_month
            dayMode -> Res.string.date_next_day
            else -> Res.string.date_next_month
        },
    )
    val unit = stringResource(if (dayMode) Res.string.unit_day else Res.string.unit_month)
    TextButton(
        onClick = onClick,
        modifier = Modifier
            .sizeIn(minHeight = 48.dp)
            .semantics { contentDescription = description },
    ) {
        Text(
            if (compact) {
                if (previous) "‹ $unit" else "$unit ›"
            } else {
                if (previous) "‹ $description" else "$description ›"
            },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun ModeSwitcher(
    todayMode: Boolean,
    onModeChange: (Boolean) -> Unit,
    compact: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier
            .height(40.dp)
            .width(if (compact) 144.dp else 180.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f))
            .padding(3.dp),
    ) {
        ModeSwitcherItem(
            label = stringResource(Res.string.mode_day),
            selected = todayMode,
            onClick = { onModeChange(true) },
            modifier = Modifier.weight(1f),
        )
        ModeSwitcherItem(
            label = stringResource(Res.string.mode_month),
            selected = !todayMode,
            onClick = { onModeChange(false) },
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun ModeSwitcherItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Surface(
        modifier = modifier.fillMaxHeight(),
        shape = RoundedCornerShape(9.dp),
        color = if (selected) MaterialTheme.colorScheme.surface else Color.Transparent,
        shadowElevation = if (selected) 1.dp else 0.dp,
        onClick = onClick,
    ) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                label,
                style = MaterialTheme.typography.labelLarge,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Preview
@Composable
private fun MonthTopBarDayPreview() {
    HabitSheetTheme(darkTheme = false) {
        Surface { MonthTopBar(MonthPreviewData.state(todayMode = true), MonthActions(), compact = true) }
    }
}

@Preview(widthDp = 900)
@Composable
private fun MonthTopBarMonthTabletPreview() {
    HabitSheetTheme(darkTheme = false) {
        Surface { MonthTopBar(MonthPreviewData.state(todayMode = false), MonthActions(), compact = false) }
    }
}
