package com.tutushubham.pokidex.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.tutushubham.pokidex.ui.theme.AppShapes
import com.tutushubham.pokidex.ui.theme.AppSizes
import com.tutushubham.pokidex.ui.theme.AppSpacing

@Composable
fun LoadingView(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator(modifier = Modifier.size(AppSizes.iconXl))
    }
}

@Composable
fun ErrorView(
    message: String,
    onRetry: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(AppSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = "Something went wrong",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.error
        )
        Spacer(modifier = Modifier.height(AppSpacing.sm))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (onRetry != null) {
            Spacer(modifier = Modifier.height(AppSpacing.lg))
            Button(onClick = onRetry) {
                Text("Retry")
            }
        }
    }
}

@Composable
fun EmptyStateView(
    title: String,
    message: String,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(AppSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(AppSpacing.sm))
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
fun ShimmerLoadingView(modifier: Modifier = Modifier) {
    val shimmerColor = MaterialTheme.colorScheme.surfaceVariant
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.7f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 800),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmerAlpha"
    )
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(AppSpacing.lg),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)
    ) {
        // Header skeleton
        Box(
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(AppSpacing.xxl)
                .clip(AppShapes.small)
                .background(shimmerColor.copy(alpha = alpha))
        )
        Box(
            modifier = Modifier
                .fillMaxWidth(0.4f)
                .height(AppSpacing.lg)
                .clip(AppShapes.small)
                .background(shimmerColor.copy(alpha = alpha))
        )
        Spacer(modifier = Modifier.height(AppSpacing.sm))
        // Card skeletons
        repeat(3) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.medium,
                tonalElevation = AppSpacing.xs
            ) {
                Column(modifier = Modifier.padding(AppSpacing.lg)) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(AppSpacing.lg)
                            .clip(AppShapes.small)
                            .background(shimmerColor.copy(alpha = alpha))
                    )
                    Spacer(modifier = Modifier.height(AppSpacing.sm))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(AppSizes.iconXl)
                            .clip(AppShapes.small)
                            .background(shimmerColor.copy(alpha = alpha))
                    )
                    Spacer(modifier = Modifier.height(AppSpacing.sm))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.5f)
                            .height(AppSpacing.md)
                            .clip(AppShapes.small)
                            .background(shimmerColor.copy(alpha = alpha))
                    )
                }
            }
        }
    }
}

@Composable
fun SyncErrorView(
    title: String = "We couldn\u2019t load your plan.",
    subtitle: String = "A temporary sync error occurred. Let\u2019s try that again.",
    errorCode: String? = null,
    onRetry: () -> Unit,
    onDismiss: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(AppSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = AppShapes.large,
            color = MaterialTheme.colorScheme.errorContainer,
            modifier = Modifier.size(AppSizes.ringSmall - AppSizes.iconSmall)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text("⚠️", style = MaterialTheme.typography.headlineLarge)
            }
        }
        Spacer(modifier = Modifier.height(AppSpacing.xxl))
        Text(
            text = "SYSTEM ALERT",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary
        )
        Spacer(modifier = Modifier.height(AppSpacing.sm))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(AppSpacing.md))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(AppSpacing.xxxl))
        Button(
            onClick = onRetry,
            modifier = Modifier.fillMaxWidth(0.6f),
            shape = AppShapes.medium
        ) {
            Text("Retry Sync")
        }
        if (onDismiss != null) {
            Spacer(modifier = Modifier.height(AppSpacing.md))
            TextButton(onClick = onDismiss) {
                Text("Go to Dashboard")
            }
        }
        if (errorCode != null) {
            Spacer(modifier = Modifier.height(AppSpacing.xxl))
            Text(
                text = "Error Code: $errorCode",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun CompletionView(
    title: String = "You\u2019re done for today",
    subtitle: String = "You have completed all planned sessions.\nTake some time to recover and reflect.",
    insightTitle: String? = null,
    onReviewInsights: (() -> Unit)? = null,
    onPlanTomorrow: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(AppSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            shape = AppShapes.large,
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(AppSizes.ringSmall - AppSizes.iconSmall)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    "✓",
                    style = MaterialTheme.typography.headlineLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
        Spacer(modifier = Modifier.height(AppSpacing.xxl))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        Spacer(modifier = Modifier.height(AppSpacing.md))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        if (insightTitle != null) {
            Spacer(modifier = Modifier.height(AppSpacing.xxl))
            Surface(
                shape = AppShapes.medium,
                tonalElevation = AppSpacing.xs,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(AppSpacing.lg)) {
                    Text(
                        text = "PERFORMANCE INSIGHT",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.height(AppSpacing.sm))
                    Text(text = insightTitle, style = MaterialTheme.typography.titleMedium)
                    if (onReviewInsights != null) {
                        Spacer(modifier = Modifier.height(AppSpacing.md))
                        Button(
                            onClick = onReviewInsights,
                            shape = AppShapes.medium
                        ) {
                            Text("Review Insights")
                        }
                    }
                }
            }
        }
        if (onPlanTomorrow != null) {
            Spacer(modifier = Modifier.height(AppSpacing.lg))
            TextButton(onClick = onPlanTomorrow) {
                Text("Plan for tomorrow")
            }
        }
    }
}

@Composable
fun <T> StateContent(
    isLoading: Boolean,
    error: String?,
    data: T?,
    onRetry: () -> Unit,
    shimmer: Boolean = true,
    modifier: Modifier = Modifier,
    content: @Composable (T) -> Unit
) {
    when {
        isLoading -> if (shimmer) ShimmerLoadingView(modifier) else LoadingView(modifier)
        error != null -> SyncErrorView(onRetry = onRetry, modifier = modifier)
        data != null -> content(data)
        else -> EmptyStateView(title = "Nothing here", message = "No data available.", modifier = modifier)
    }
}
