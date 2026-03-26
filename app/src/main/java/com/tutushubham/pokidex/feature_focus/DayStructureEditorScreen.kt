package com.tutushubham.pokidex.feature_focus

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.tutushubham.pokidex.core.domain.entity.Anchor
import com.tutushubham.pokidex.core.domain.model.DayBlock
import com.tutushubham.pokidex.ui.components.InsightCard
import com.tutushubham.pokidex.ui.components.MetricCard
import com.tutushubham.pokidex.ui.components.TimelineBlock
import com.tutushubham.pokidex.ui.theme.AppShapes
import com.tutushubham.pokidex.ui.theme.AppSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DayStructureEditorScreen(
    anchors: List<Anchor>,
    totalAvailableHours: Float,
    onAddBlock: (DayBlock) -> Unit,
    onDeleteBlock: (String) -> Unit,
    onUpdateSchedule: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "STRUCTURE EDITOR",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text("Optimize Your Day", fontWeight = FontWeight.Bold)
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { onAddBlock(DayBlock.MORNING) }) {
                Icon(Icons.Default.Add, contentDescription = "Add Block")
            }
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

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MetricCard(
                    title = "Available",
                    value = "%.1f hrs".format(totalAvailableHours),
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Blocks",
                    value = "${anchors.size}",
                    modifier = Modifier.weight(1f).padding(start = AppSpacing.sm)
                )
            }

            Spacer(modifier = Modifier.height(AppSpacing.xxl))

            Text(
                text = "Active Timeline",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
            Spacer(modifier = Modifier.height(AppSpacing.md))

            if (anchors.isEmpty()) {
                Surface(
                    shape = AppShapes.medium,
                    tonalElevation = AppSpacing.xs,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(AppSpacing.xxl),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("No blocks configured")
                        Spacer(modifier = Modifier.height(AppSpacing.sm))
                        Text(
                            "Add time blocks to structure your day.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                val sortedAnchors = anchors.sortedBy { it.block.ordinal }
                sortedAnchors.forEachIndexed { index, anchor ->
                    TimelineBlock(
                        timeLabel = anchor.block.name.take(3),
                        title = "${anchor.domain.name} • ${anchor.defaultMinutes}m",
                        subtitle = "Block: ${anchor.block.name}",
                        isActive = index == 0,
                        isLast = index == sortedAnchors.lastIndex
                    )
                }
            }

            Spacer(modifier = Modifier.height(AppSpacing.xxl))

            InsightCard(
                icon = "💡",
                title = "Structure Insight",
                subtitle = "Your current allocation provides ${totalAvailableHours} hours of focus time. Balance blocks for cognitive variety.",
                containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                contentColor = MaterialTheme.colorScheme.onTertiaryContainer
            )

            Spacer(modifier = Modifier.height(AppSpacing.xxl))

            Button(
                onClick = onUpdateSchedule,
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.medium
            ) {
                Text("Update Schedule")
            }

            Spacer(modifier = Modifier.height(AppSpacing.xxxl))
        }
    }
}
