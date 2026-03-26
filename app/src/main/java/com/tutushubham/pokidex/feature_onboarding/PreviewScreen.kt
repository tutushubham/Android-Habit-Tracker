package com.tutushubham.pokidex.feature_onboarding

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.tutushubham.pokidex.ui.components.AppCard
import com.tutushubham.pokidex.ui.components.InsightCard
import com.tutushubham.pokidex.ui.components.PrimaryButton
import com.tutushubham.pokidex.ui.components.SecondaryButton
import com.tutushubham.pokidex.ui.components.TimelineBlock
import com.tutushubham.pokidex.ui.theme.AppShapes
import com.tutushubham.pokidex.ui.theme.AppSpacing

@Composable
fun PreviewScreen(
    state: OnboardingContract.State,
    onBack: () -> Unit,
    onFinish: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    val scroll = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(AppSpacing.lg)
    ) {

        Text(
            text = "Plan preview",
            style = MaterialTheme.typography.headlineMedium
        )

        Spacer(Modifier.height(AppSpacing.sm))

        Text(
            text = "You can always adjust later.",
            style = MaterialTheme.typography.bodyMedium,
            color = scheme.onSurfaceVariant
        )

        Spacer(Modifier.height(AppSpacing.xxl))

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(scroll),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            if (state.previewLines.isEmpty()) {
                Text(
                    text = "No sessions generated yet. Complete earlier steps to see a sample day.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = scheme.onSurfaceVariant
                )
            } else {
                Text(
                    text = "Tomorrow’s timeline",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(Modifier.height(AppSpacing.md))

                state.previewLines.forEachIndexed { index, line ->
                    val (timeLabel, title, subtitle) = parsePreviewLine(line)
                    TimelineBlock(
                        timeLabel = timeLabel.ifBlank { "—" },
                        title = title.ifBlank { line },
                        subtitle = subtitle.ifBlank { "" },
                        isActive = index == 0,
                        isLast = index == state.previewLines.lastIndex
                    )
                }
            }

            Spacer(Modifier.height(AppSpacing.xxl))

            InsightCard(
                icon = "✨",
                title = "Cognitive clearance",
                subtitle = "This preview balances your blocks, domains, and strategies so tomorrow starts with a clear queue — no mental tab-switching before you begin.",
                containerColor = scheme.tertiaryContainer,
                contentColor = scheme.onTertiaryContainer
            )

            Spacer(Modifier.height(AppSpacing.lg))

            AppCard(
                shape = AppShapes.large,
                containerColor = scheme.primaryContainer.copy(alpha = 0.55f),
                contentPadding = Modifier.padding(AppSpacing.xxl)
            ) {
                Text(
                    text = "🎉",
                    style = MaterialTheme.typography.displaySmall,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(AppSpacing.md))
                Text(
                    text = "You’re ready to begin",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = scheme.onPrimaryContainer,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(AppSpacing.sm))
                Text(
                    text = "Finish setup to save your plan and open your sanctuary.",
                    style = MaterialTheme.typography.bodySmall,
                    color = scheme.onPrimaryContainer.copy(alpha = 0.9f),
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }

        Spacer(Modifier.height(AppSpacing.lg))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)
        ) {
            SecondaryButton(
                text = "Back",
                onClick = onBack,
                modifier = Modifier.weight(1f)
            )
            PrimaryButton(
                text = "Finish setup",
                onClick = onFinish,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

private fun parsePreviewLine(line: String): Triple<String, String, String> {
    val parts = line.split(" → ").map { it.trim() }
    return when {
        parts.size >= 4 ->
            Triple(
                parts[0],
                "${parts[1]} · ${parts[2]}",
                parts[3]
            )
        parts.size == 3 -> Triple(parts[0], parts[1], parts[2])
        parts.size == 2 -> Triple(parts[0], parts[1], "")
        else -> Triple("", line, "")
    }
}
