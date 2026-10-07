package com.habitsheet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.habitsheet.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
internal fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

@Composable
internal fun SubtleProgress(progress: Double, modifier: Modifier = Modifier) {
    val progressValue = progress.toFloat().coerceIn(0f, 1f)
    Box(
        modifier
            .height(2.dp)
            .background(MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(1.dp))
            .semantics {
                progressBarRangeInfo = androidx.compose.ui.semantics.ProgressBarRangeInfo(progressValue, 0f..1f)
            },
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(progressValue)
                .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(1.dp)),
        )
    }
}

@Composable
internal fun BottomNavigation(
    selectedTracker: Boolean,
    onTracker: () -> Unit,
    onManage: () -> Unit,
    onPosition: (String, Rect) -> Unit = { _, _ -> },
) {
    val dividerColor = MaterialTheme.colorScheme.outlineVariant
    Row(
        Modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .height(64.dp)
            .background(MaterialTheme.colorScheme.surface)
            .drawBehind {
                drawLine(dividerColor, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = 1.dp.toPx())
            },
    ) {
        BottomNavItem(HabitIconGlyph.Tracker, stringResource(Res.string.nav_tracker), selectedTracker, Modifier.weight(1f), onTracker)
        BottomNavItem(
            HabitIconGlyph.Manage,
            stringResource(Res.string.nav_habits),
            !selectedTracker,
            Modifier.weight(1f).tutorialTarget("manage", onPosition),
            onManage,
        )
    }
}

@Composable
private fun BottomNavItem(icon: HabitIconGlyph, label: String, selected: Boolean, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.fillMaxHeight().clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        HabitIcon(
            icon,
            Modifier.size(21.dp),
            if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
