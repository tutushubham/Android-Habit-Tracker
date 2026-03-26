package com.tutushubham.pokidex.feature_focus

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.core.domain.model.FocusStrategy
import com.tutushubham.pokidex.core.domain.model.ThemeMode
import com.tutushubham.pokidex.ui.theme.AppShapes
import com.tutushubham.pokidex.ui.theme.AppSizes
import com.tutushubham.pokidex.ui.theme.AppSpacing
import com.tutushubham.pokidex.ui.theme.PokidexTheme

private fun domainEmoji(domain: Domain): String = when (domain) {
    Domain.STUDIES -> "📚"
    Domain.FITNESS -> "💪"
    Domain.WORK -> "💼"
    Domain.HOBBY -> "🎯"
}

private fun domainDescription(domain: Domain): String = when (domain) {
    Domain.STUDIES -> "Algorithms & Data Structures"
    Domain.FITNESS -> "Physical Training & Wellness"
    Domain.WORK -> "Professional Development"
    Domain.HOBBY -> "Creative Pursuits & Exploration"
}

private fun strategyTitle(strategy: FocusStrategy?): String = when (strategy) {
    is FocusStrategy.Manual -> "Manual Selection"
    is FocusStrategy.Rotation -> "Smart Rotation"
    is FocusStrategy.Weighted -> "Deep Work Flow"
    is FocusStrategy.DeadlineDriven -> "Deadline Priority"
    null -> "No Strategy"
}

private fun strategyDescription(strategy: FocusStrategy?): String = when (strategy) {
    is FocusStrategy.Manual -> "You choose which focus to work on"
    is FocusStrategy.Rotation -> "Cycle through focuses systematically"
    is FocusStrategy.Weighted -> "Zero-distraction protocol"
    is FocusStrategy.DeadlineDriven -> "Urgent deadlines first"
    null -> "Select a strategy to optimize your flow"
}

private fun domainHeadline(domain: Domain, currentFocusTitle: String?): String =
    currentFocusTitle ?: when (domain) {
        Domain.STUDIES -> "Studies"
        Domain.FITNESS -> "Fitness"
        Domain.WORK -> "Work"
        Domain.HOBBY -> "Hobby"
    }

private data class StrategyInsight(
    val body: String,
    val tip: String
)

private fun strategyInsight(strategy: FocusStrategy?): StrategyInsight = when (strategy) {
    is FocusStrategy.Manual ->
        StrategyInsight(
            body = "Manual mode keeps you in control—pick the focus that matches your energy each day.",
            tip = "\"Start with the smallest actionable step to build momentum.\""
        )
    is FocusStrategy.Rotation ->
        StrategyInsight(
            body = "Rotation spreads attention evenly so no goal stalls while another hogs the week.",
            tip = "\"Trust the cycle; consistency beats heroic single-day pushes.\""
        )
    is FocusStrategy.Weighted ->
        StrategyInsight(
            body = "Weighted flow prioritizes high-impact work while still touching secondary goals.",
            tip = "\"Protect deep blocks—notifications are not part of the protocol.\""
        )
    is FocusStrategy.DeadlineDriven ->
        StrategyInsight(
            body = "Deadline priority surfaces what must ship soonest, reducing last-minute panic.",
            tip = "\"Work backward from the due date and leave buffer for the unexpected.\""
        )
    null ->
        StrategyInsight(
            body = "Choosing a strategy helps the app tailor your week and reduce decision fatigue.",
            tip = "\"Go back one step and pick the mode that fits how you actually work.\""
        )
}

@Composable
fun FocusConfirmScreen(
    domain: Domain,
    strategy: FocusStrategy?,
    preview: List<String>,
    currentFocusTitle: String?,
    onConfirm: () -> Unit,
    onBack: () -> Unit = {}
) {
    val colorScheme = MaterialTheme.colorScheme
    val primaryDim = colorScheme.primary.copy(alpha = 0.75f)
    val insight = strategyInsight(strategy)
    val uniqueFocusCount = preview.distinct().size
    val roadmap = preview.take(3)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppSpacing.lg)
            .padding(bottom = AppSpacing.xxxl)
    ) {
        Spacer(modifier = Modifier.height(AppSpacing.md))

        Box(modifier = Modifier.fillMaxWidth()) {
            IconButton(
                onClick = onBack,
                modifier = Modifier.align(Alignment.CenterStart)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = colorScheme.onSurface
                )
            }
            Text(
                text = "SESSION CONFIGURATION",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.primary,
                modifier = Modifier.align(Alignment.Center)
            )
            IconButton(
                onClick = { },
                modifier = Modifier.align(Alignment.CenterEnd)
            ) {
                Icon(
                    imageVector = Icons.Filled.Settings,
                    contentDescription = "Settings",
                    tint = colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(AppSpacing.lg))

        Text(
            text = "Ready for Immersion?",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(AppSpacing.xxl))

        InfoCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = domainEmoji(domain),
                    style = MaterialTheme.typography.headlineSmall,
                    modifier = Modifier.padding(end = AppSpacing.md)
                )
                Column {
                    Text(
                        text = "Focus Domain",
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(AppSpacing.xs))
                    Text(
                        text = domainHeadline(domain, currentFocusTitle),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(AppSpacing.sm))
                    Text(
                        text = domainDescription(domain),
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(AppSpacing.md))

        InfoCard {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(AppSizes.iconXl)
                        .background(colorScheme.secondaryContainer, AppShapes.medium),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = colorScheme.onSecondaryContainer
                    )
                }
                Spacer(modifier = Modifier.width(AppSpacing.md))
                Column {
                    Text(
                        text = "Strategy",
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(AppSpacing.xs))
                    Text(
                        text = strategyTitle(strategy),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(AppSpacing.sm))
                    Text(
                        text = strategyDescription(strategy),
                        style = MaterialTheme.typography.bodySmall,
                        color = colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(AppSpacing.md))

        InfoCard {
            Text(
                text = "7-Day Preview",
                style = MaterialTheme.typography.labelMedium,
                color = colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(AppSpacing.sm))
            Text(
                text = "$uniqueFocusCount Focus Areas",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(AppSpacing.md))
            Text(
                text = "Roadmap",
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(AppSpacing.sm))
            roadmap.forEachIndexed { index, name ->
                Text(
                    text = "${index + 1}. $name",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurface
                )
                if (index < roadmap.lastIndex) {
                    Spacer(modifier = Modifier.height(AppSpacing.xs))
                }
            }
            if (roadmap.isEmpty()) {
                Text(
                    text = "Add focuses to see your week mapped out.",
                    style = MaterialTheme.typography.bodySmall,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(AppSpacing.md))

        Surface(
            shape = AppShapes.large,
            color = colorScheme.tertiaryContainer,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(AppSpacing.lg)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        tint = colorScheme.onTertiaryContainer,
                        modifier = Modifier.size(AppSizes.iconSmall)
                    )
                    Spacer(modifier = Modifier.width(AppSpacing.sm))
                    Text(
                        text = "AI Strategy Insight",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onTertiaryContainer
                    )
                }
                Spacer(modifier = Modifier.height(AppSpacing.md))
                Text(
                    text = insight.body,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onTertiaryContainer
                )
                Spacer(modifier = Modifier.height(AppSpacing.md))
                Text(
                    text = insight.tip,
                    style = MaterialTheme.typography.bodySmall,
                    fontStyle = FontStyle.Italic,
                    color = colorScheme.onTertiaryContainer.copy(alpha = 0.9f)
                )
            }
        }

        Spacer(modifier = Modifier.height(AppSpacing.xxl))

        Button(
            onClick = onConfirm,
            modifier = Modifier
                .fillMaxWidth()
                .height(AppSizes.buttonHeight),
            shape = AppShapes.pill,
            colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
            contentPadding = PaddingValues(0.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(
                        brush = Brush.horizontalGradient(
                            colors = listOf(primaryDim, colorScheme.primary)
                        )
                    ),
                contentAlignment = Alignment.Center
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Confirm & Start",
                        style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                        color = colorScheme.onPrimary
                    )
                    Spacer(modifier = Modifier.width(AppSpacing.md))
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = null,
                        tint = colorScheme.onPrimary
                    )
                }
            }
        }

        TextButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "EDIT PARAMETERS",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = colorScheme.primary
            )
        }
    }
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    Surface(
        shape = AppShapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(AppSpacing.lg)) {
            content()
        }
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FocusConfirmScreenPreview() {
    PokidexTheme(themeMode = ThemeMode.DARK, dynamicColor = false) {
        FocusConfirmScreen(
            domain = Domain.STUDIES,
            strategy = FocusStrategy.Weighted(weights = mapOf("a" to 1)),
            preview = listOf("DSA", "Android", "DSA", "System Design", "DSA", "Android", "Rest"),
            currentFocusTitle = "DSA Mastery",
            onConfirm = {},
            onBack = {}
        )
    }
}

@Preview(showBackground = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun FocusConfirmScreenShortPreview() {
    PokidexTheme(themeMode = ThemeMode.DARK, dynamicColor = false) {
        FocusConfirmScreen(
            domain = Domain.FITNESS,
            strategy = null,
            preview = listOf("DSA", "Android"),
            currentFocusTitle = null,
            onConfirm = {},
            onBack = {}
        )
    }
}
