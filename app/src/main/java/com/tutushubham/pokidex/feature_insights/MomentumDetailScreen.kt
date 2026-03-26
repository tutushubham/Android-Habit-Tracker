package com.tutushubham.pokidex.feature_insights

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.tutushubham.pokidex.core.engine.ExplainabilityEngine
import com.tutushubham.pokidex.core.engine.UserBehaviorProfile
import com.tutushubham.pokidex.ui.components.ActionCard
import com.tutushubham.pokidex.ui.components.MetricCard
import com.tutushubham.pokidex.ui.components.MetricRing
import com.tutushubham.pokidex.ui.theme.AppSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MomentumDetailScreen(
    profile: UserBehaviorProfile,
    onBack: () -> Unit,
    onKeepStreak: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val explanation = ExplainabilityEngine.explainMomentum(profile)
    val momentumProgress = when {
        profile.momentum.streakDays >= 7 -> 0.95f
        profile.momentum.streakDays >= 3 -> 0.65f
        profile.momentum.isConsistent -> 0.45f
        else -> 0.2f
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Momentum Detail") },
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
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(AppSpacing.xxl))
            MetricRing(
                progress = momentumProgress,
                label = explanation.title.split(" ").first(),
                sublabel = "MOMENTUM"
            )
            Spacer(modifier = Modifier.height(AppSpacing.xxl))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                MetricCard(
                    title = "Streak",
                    value = "${profile.momentum.streakDays} days",
                    modifier = Modifier.weight(1f)
                )
                MetricCard(
                    title = "Recent completion",
                    value = "${(profile.momentum.recentCompletionRate * 100).toInt()}%",
                    modifier = Modifier.weight(1f).padding(start = AppSpacing.sm)
                )
            }

            Spacer(modifier = Modifier.height(AppSpacing.xxl))

            Text(
                text = "Analysis",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(AppSpacing.sm))
            Text(
                text = explanation.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(AppSpacing.xxl))

            ActionCard(
                icon = "🔥",
                title = "Keep the streak",
                message = "Protect your momentum with consistent session completion this week.",
                primaryActionLabel = "View today",
                onPrimaryAction = onKeepStreak
            )

            Spacer(modifier = Modifier.height(AppSpacing.xxxl))
        }
    }
}
