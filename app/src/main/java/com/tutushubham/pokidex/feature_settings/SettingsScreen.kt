package com.tutushubham.pokidex.feature_settings

import androidx.compose.foundation.background
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tutushubham.pokidex.ui.components.AppCard
import com.tutushubham.pokidex.ui.components.AppChip
import com.tutushubham.pokidex.ui.components.SectionHeader
import com.tutushubham.pokidex.ui.theme.AppShapes
import com.tutushubham.pokidex.ui.theme.AppSpacing
import com.tutushubham.pokidex.ui.theme.PokidexTheme

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    if (state.showResetConfirmation) {
        ResetConfirmationDialog(
            onConfirm = viewModel::confirmResetBehavior,
            onDismiss = viewModel::dismissResetConfirmation
        )
    }

    SettingsContent(
        settings = state.settings,
        onUpdate = viewModel::updateSettings,
        onResetBehavior = viewModel::showResetConfirmation,
        onBack = onBack,
        modifier = modifier
    )
}

@Composable
private fun SettingsContent(
    settings: SystemSettings,
    onUpdate: ((SystemSettings) -> SystemSettings) -> Unit,
    onResetBehavior: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .verticalScroll(rememberScrollState())
    ) {
        SettingsTopBar(onBack = onBack)

        Spacer(Modifier.height(AppSpacing.sm))

        SectionHeader(
            overline = "APPEARANCE",
            title = "Visual Theme",
            modifier = Modifier.padding(horizontal = AppSpacing.xl)
        )
        Spacer(Modifier.height(AppSpacing.sm))
        ThemeModeSelector(
            selected = settings.themeMode,
            onSelect = { mode -> onUpdate { it.copy(themeMode = mode) } }
        )

        Spacer(Modifier.height(AppSpacing.xxxl))

        SectionHeader(
            overline = "INTELLIGENCE",
            title = "Adaptive Planning",
            modifier = Modifier.padding(horizontal = AppSpacing.xl)
        )
        Spacer(Modifier.height(AppSpacing.sm))

        SettingsToggle(
            title = "Adaptive Planning",
            subtitle = "Let the engine adjust session allocation based on your pace and behavior.",
            checked = settings.adaptivePlanningEnabled,
            onToggle = { onUpdate { it.copy(adaptivePlanningEnabled = !it.adaptivePlanningEnabled) } }
        )

        Spacer(Modifier.height(AppSpacing.lg))

        SelectorRow(
            title = "Planning Style",
            options = PlanningStyle.entries,
            selected = settings.planningStyle,
            labelOf = { it.label },
            onSelect = { style -> onUpdate { it.copy(planningStyle = style) } }
        )
        Text(
            text = settings.planningStyle.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = AppSpacing.xl),
            fontSize = 11.sp
        )

        Spacer(Modifier.height(AppSpacing.lg))

        SelectorRow(
            title = "Fatigue Sensitivity",
            options = FatigueSensitivity.entries,
            selected = settings.fatigueSensitivity,
            labelOf = { it.label },
            onSelect = { level -> onUpdate { it.copy(fatigueSensitivity = level) } }
        )
        Text(
            text = settings.fatigueSensitivity.description,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = AppSpacing.xl),
            fontSize = 11.sp
        )

        Spacer(Modifier.height(AppSpacing.xxxl))

        SectionHeader(
            overline = "LEARNING",
            title = "Session Learning",
            modifier = Modifier.padding(horizontal = AppSpacing.xl)
        )
        Spacer(Modifier.height(AppSpacing.sm))

        SettingsToggle(
            title = "Learn From Sessions",
            subtitle = "Estimate duration per unit adapts based on your actual completion times.",
            checked = settings.learningEnabled,
            onToggle = { onUpdate { it.copy(learningEnabled = !it.learningEnabled) } }
        )

        Spacer(Modifier.height(AppSpacing.md))

        ResetButton(onClick = onResetBehavior)

        Spacer(Modifier.height(AppSpacing.xxxl))

        SectionHeader(
            overline = "FOCUS",
            title = "Peak Focus Time",
            modifier = Modifier.padding(horizontal = AppSpacing.xl)
        )
        Spacer(Modifier.height(AppSpacing.sm))

        SettingsToggle(
            title = "Use AI Peak Time",
            subtitle = "Automatically detect your best focus hours from session history.",
            checked = settings.useAiPeakTime,
            onToggle = { onUpdate { it.copy(useAiPeakTime = !it.useAiPeakTime) } }
        )

        if (!settings.useAiPeakTime) {
            Spacer(Modifier.height(AppSpacing.md))
            PeakTimeOverride(
                startHour = settings.peakFocusStartHour,
                endHour = settings.peakFocusEndHour,
                onStartChange = { h -> onUpdate { it.copy(peakFocusStartHour = h) } },
                onEndChange = { h -> onUpdate { it.copy(peakFocusEndHour = h) } }
            )
        }

        Spacer(Modifier.height(AppSpacing.xxxl))

        SectionHeader(
            overline = "NOTIFICATIONS",
            title = "Alerts & Reminders",
            modifier = Modifier.padding(horizontal = AppSpacing.xl)
        )
        Spacer(Modifier.height(AppSpacing.sm))

        SettingsToggle(
            title = "Daily Summary",
            subtitle = "Get a morning briefing with your planned sessions.",
            checked = settings.notifyDailySummary,
            onToggle = { onUpdate { it.copy(notifyDailySummary = !it.notifyDailySummary) } }
        )

        SettingsToggle(
            title = "Peak Reminder",
            subtitle = "Remind you when your peak focus window starts.",
            checked = settings.notifyPeakReminder,
            onToggle = { onUpdate { it.copy(notifyPeakReminder = !it.notifyPeakReminder) } }
        )

        SettingsToggle(
            title = "Behind Alert",
            subtitle = "Alert when you fall behind on a critical goal.",
            checked = settings.notifyBehindAlert,
            onToggle = { onUpdate { it.copy(notifyBehindAlert = !it.notifyBehindAlert) } }
        )

        Spacer(Modifier.height(AppSpacing.xxxxl))
    }
}

@Composable
private fun SettingsTopBar(onBack: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Text(
            "Settings",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold
        )
    }
}

@Composable
private fun ThemeModeSelector(
    selected: ThemeMode,
    onSelect: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.xl),
        shape = AppShapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(AppSpacing.xs),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ThemeMode.entries.forEach { mode ->
                val isSelected = mode == selected
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .clip(AppShapes.medium)
                        .background(
                            if (isSelected) MaterialTheme.colorScheme.surface
                            else MaterialTheme.colorScheme.surfaceContainerLow
                        )
                        .clickable { onSelect(mode) }
                        .padding(vertical = AppSpacing.md),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = mode.name.lowercase().replaceFirstChar { it.uppercase() },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                        color = if (isSelected) MaterialTheme.colorScheme.primary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsToggle(
    title: String,
    subtitle: String,
    checked: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    AppCard(
        modifier = modifier.padding(horizontal = AppSpacing.xl, vertical = AppSpacing.xs)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
            Spacer(Modifier.width(AppSpacing.md))
            Switch(checked = checked, onCheckedChange = { onToggle() })
        }
    }
}

@Composable
private fun <T> SelectorRow(
    title: String,
    options: List<T>,
    selected: T,
    labelOf: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.padding(horizontal = AppSpacing.xl)) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = AppSpacing.sm)
        )
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
            options.forEach { option ->
                AppChip(
                    label = labelOf(option),
                    selected = option == selected,
                    onClick = { onSelect(option) }
                )
            }
        }
    }
}

@Composable
private fun PeakTimeOverride(
    startHour: Int,
    endHour: Int,
    onStartChange: (Int) -> Unit,
    onEndChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    AppCard(
        modifier = modifier.padding(horizontal = AppSpacing.xl)
    ) {
        Text(
            "Manual Peak Hours",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(AppSpacing.md))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TimeSelector(label = "START", hour = startHour, onChange = onStartChange)
            Text(
                "→",
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            TimeSelector(label = "END", hour = endHour, onChange = onEndChange)
        }
    }
}

@Composable
private fun TimeSelector(
    label: String,
    hour: Int,
    onChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.sp,
            fontSize = 9.sp
        )
        Spacer(Modifier.height(AppSpacing.xs))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Surface(
                modifier = Modifier.size(AppSpacing.xxxl),
                shape = AppShapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                onClick = { onChange((hour - 1).coerceAtLeast(0)) }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("−", fontWeight = FontWeight.Bold)
                }
            }
            Spacer(Modifier.width(AppSpacing.md))
            Text(
                text = formatHour(hour),
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.width(AppSpacing.md))
            Surface(
                modifier = Modifier.size(AppSpacing.xxxl),
                shape = AppShapes.small,
                color = MaterialTheme.colorScheme.surfaceContainerHigh,
                onClick = { onChange((hour + 1).coerceAtMost(23)) }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text("+", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
private fun ResetButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    androidx.compose.material3.Button(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.xl),
        shape = AppShapes.large,
        colors = ButtonDefaults.buttonColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer
        )
    ) {
        Text(
            "Reset Learned Behavior",
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(vertical = AppSpacing.xs)
        )
    }
}

@Composable
private fun ResetConfirmationDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Reset All Learned Data?", fontWeight = FontWeight.Bold) },
        text = {
            Text("This will clear all learned session estimates and behavior profiles. The system will re-learn from your future sessions.")
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Reset", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

private fun formatHour(hour: Int): String = when {
    hour == 0 -> "12 AM"
    hour < 12 -> "$hour AM"
    hour == 12 -> "12 PM"
    else -> "${hour - 12} PM"
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun SettingsScreenPreview() {
    PokidexTheme {
        SettingsContent(
            settings = SystemSettings(),
            onUpdate = {},
            onResetBehavior = {},
            onBack = {}
        )
    }
}

@Preview(showBackground = true, showSystemUi = true)
@Composable
private fun SettingsScreenManualPeakPreview() {
    PokidexTheme {
        SettingsContent(
            settings = SystemSettings(
                useAiPeakTime = false,
                planningStyle = PlanningStyle.STRICT,
                fatigueSensitivity = FatigueSensitivity.HIGH
            ),
            onUpdate = {},
            onResetBehavior = {},
            onBack = {}
        )
    }
}
