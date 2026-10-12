package com.habitsheet.ui.month

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitsheet.platform.rememberReduceMotion
import com.habitsheet.presentation.MonthUiState
import com.habitsheet.resources.*
import com.habitsheet.ui.HabitSheetTheme
import com.habitsheet.ui.SubtleProgress
import com.habitsheet.ui.categoryColor
import com.habitsheet.ui.fullDateLabel
import com.habitsheet.ui.percentLabel
import com.habitsheet.ui.scaledForText
import com.habitsheet.ui.weekdayLabel
import com.habitsheet.ui.weekdayName
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun CompletionCell(
    completed: Boolean,
    currentDay: Boolean,
    enabled: Boolean = true,
    description: String,
    width: Dp,
    height: Dp,
    onClick: () -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val statusDescription = stringResource(
        when {
            !enabled -> Res.string.status_not_planned
            completed -> Res.string.status_completed
            else -> Res.string.status_incomplete
        },
    )
    Box(
        Modifier
            .width(width)
            .height(height)
            .background(
                when {
                    !enabled -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.2f)
                    currentDay -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                    else -> Color.Transparent
                },
            )
            .semantics {
                contentDescription = description
                stateDescription = statusDescription
            }
            .toggleable(
                value = completed && enabled,
                enabled = enabled,
                role = Role.Checkbox,
            ) {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                onClick()
            },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(22.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(if (completed && enabled) MaterialTheme.colorScheme.primary else Color.Transparent)
                .border(
                    BorderStroke(
                        if (completed && enabled) 0.dp else 1.dp,
                        if (completed && enabled) Color.Transparent else MaterialTheme.colorScheme.outline,
                    ),
                    RoundedCornerShape(6.dp),
                ),
            contentAlignment = Alignment.Center,
        ) {
            if (completed && enabled) {
                Text(
                    "✓",
                    color = MaterialTheme.colorScheme.onPrimary,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.clearAndSetSemantics {},
                )
            }
        }
    }
}

@Preview
@Composable
private fun CompletionCellPreview() {
    HabitSheetTheme(darkTheme = false) {
        Surface {
            Row {
                CompletionCell(true, false, true, "Read, Monday, 12 October 2026", 48.dp, 56.dp) {}
                CompletionCell(false, true, true, "Read, Tuesday, 13 October 2026", 48.dp, 56.dp) {}
                CompletionCell(false, false, false, "Read, Wednesday, 14 October 2026", 48.dp, 56.dp) {}
            }
        }
    }
}
