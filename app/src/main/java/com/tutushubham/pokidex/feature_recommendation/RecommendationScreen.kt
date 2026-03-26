package com.tutushubham.pokidex.feature_recommendation

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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tutushubham.pokidex.ui.components.ActionCard
import com.tutushubham.pokidex.ui.components.MetricCard
import com.tutushubham.pokidex.ui.components.ShimmerLoadingView
import com.tutushubham.pokidex.ui.components.SyncErrorView
import com.tutushubham.pokidex.ui.theme.AppSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun RecommendationScreen(
    viewModel: RecommendationViewModel,
    intentId: String,
    onBack: () -> Unit
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(intentId) {
        viewModel.onEvent(RecommendationContract.RecommendationEvent.ScreenOpened(intentId))
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                RecommendationContract.RecommendationEffect.NavigateBack -> onBack()
                is RecommendationContract.RecommendationEffect.ShowMessage -> {}
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Recommendation") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
        }
    ) { padding ->
        when {
            state.isLoading -> ShimmerLoadingView(modifier = Modifier.padding(padding))
            state.error != null -> SyncErrorView(
                onRetry = {
                    viewModel.onEvent(
                        RecommendationContract.RecommendationEvent.ScreenOpened(intentId)
                    )
                },
                modifier = Modifier.padding(padding)
            )
            else -> RecommendationContent(
                state = state,
                onAction = { action ->
                    viewModel.onEvent(
                        RecommendationContract.RecommendationEvent.ActionSelected(action)
                    )
                },
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun RecommendationContent(
    state: RecommendationContract.RecommendationState,
    onAction: (com.tutushubham.pokidex.core.engine.RecommendationAction) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = AppSpacing.lg)
            .verticalScroll(rememberScrollState())
    ) {
        Spacer(modifier = Modifier.height(AppSpacing.lg))
        Text(
            text = "You are behind on ${state.goalTitle}",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.height(AppSpacing.sm))
        Text(
            text = "Your current pace needs adjustment to meet your goal.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(AppSpacing.xxl))

        Text(
            text = "Velocity Analysis",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(AppSpacing.md))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            MetricCard(
                title = "Current",
                value = "%.1f".format(state.actualPace),
                subtitle = "/day",
                modifier = Modifier.weight(1f)
            )
            MetricCard(
                title = "Required",
                value = "%.1f".format(state.requiredPace),
                subtitle = "/day",
                modifier = Modifier.weight(1f).padding(start = AppSpacing.sm)
            )
        }

        Spacer(modifier = Modifier.height(AppSpacing.xxl))

        Text(
            text = "Proposed Solutions",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(AppSpacing.md))

        state.recommendations.forEach { rec ->
            ActionCard(
                icon = rec.icon,
                title = rec.title,
                message = rec.message,
                primaryActionLabel = "Apply",
                onPrimaryAction = { onAction(rec.action) }
            )
            Spacer(modifier = Modifier.height(AppSpacing.md))
        }

        Spacer(modifier = Modifier.height(AppSpacing.xxxl))
    }
}
