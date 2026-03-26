package com.tutushubham.pokidex.feature_focus

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.tutushubham.pokidex.ui.components.TimelineBlock
import com.tutushubham.pokidex.ui.components.WeeklyBarChart
import com.tutushubham.pokidex.ui.theme.AppShapes
import com.tutushubham.pokidex.ui.theme.AppSpacing
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale

data class DayFocusPreview(
    val date: LocalDate,
    val focusTitle: String,
    val description: String
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FocusPreviewScreen(
    previews: List<DayFocusPreview>,
    weeklyBalance: List<Float>,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "COGNITIVE SANCTUARY",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text("Focus Distribution Preview", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = AppSpacing.lg)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(modifier = Modifier.height(AppSpacing.lg))
            Text(
                text = "Your strategic roadmap for the upcoming cycle. Each day is optimized to maximize momentum and minimize decision fatigue.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(AppSpacing.xxl))

            previews.forEachIndexed { index, preview ->
                val dayLabel = preview.date.dayOfWeek
                    .getDisplayName(TextStyle.SHORT, Locale.getDefault())
                val dateLabel = "${preview.date.dayOfMonth}/${preview.date.monthValue}"

                TimelineBlock(
                    timeLabel = "$dayLabel\n$dateLabel",
                    title = preview.focusTitle,
                    subtitle = preview.description,
                    isActive = index == 0,
                    isLast = index == previews.lastIndex
                )
            }

            Spacer(modifier = Modifier.height(AppSpacing.xxl))

            Surface(
                shape = AppShapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(AppSpacing.lg)) {
                    Text(
                        text = "Weekly Balance",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.height(AppSpacing.md))
                    WeeklyBarChart(
                        values = weeklyBalance,
                        activeColor = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(AppSpacing.xxxl))
        }
    }
}
