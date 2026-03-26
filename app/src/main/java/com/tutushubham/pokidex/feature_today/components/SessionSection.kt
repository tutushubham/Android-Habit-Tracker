package com.tutushubham.pokidex.feature_today.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.tutushubham.pokidex.core.domain.model.SessionStatus
import com.tutushubham.pokidex.core.domain.model.SkipReason
import com.tutushubham.pokidex.feature_today.PreviewData
import com.tutushubham.pokidex.feature_today.SessionTag
import com.tutushubham.pokidex.feature_today.SessionUiModel
import com.tutushubham.pokidex.ui.theme.AppShapes
import com.tutushubham.pokidex.ui.theme.AppSizes
import com.tutushubham.pokidex.ui.theme.AppSpacing
import com.tutushubham.pokidex.ui.theme.PokidexTheme

@Composable
fun SessionSection(
    sessions: List<SessionUiModel>,
    activeSessionId: String?,
    elapsedMinutes: Int,
    onStart: (String) -> Unit,
    onSkip: (String, SkipReason) -> Unit,
    onComplete: (String, Int) -> Unit,
    onRestart: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (sessions.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.xl)
    ) {
        Text(
            text = "Today's Sessions",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(AppSpacing.md))
        sessions.forEach { session ->
            SessionCard(
                session = session,
                isActive = session.id == activeSessionId,
                elapsedMinutes = elapsedMinutes,
                onStart = { onStart(session.id) },
                onSkip = { reason -> onSkip(session.id, reason) },
                onComplete = { onComplete(session.id, elapsedMinutes) },
                onRestart = { onRestart(session.id) }
            )
            Spacer(Modifier.height(AppSpacing.md))
        }
    }
}

@Composable
fun SessionCard(
    session: SessionUiModel,
    isActive: Boolean,
    elapsedMinutes: Int,
    onStart: () -> Unit,
    onSkip: (SkipReason) -> Unit,
    onComplete: () -> Unit,
    onRestart: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isHighlighted = session.isHighlighted
    val cardColors = if (isHighlighted) {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.06f)
        )
    } else {
        CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainerLow
        )
    }
    val borderStroke = if (isHighlighted) {
        BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
    } else {
        null
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = AppShapes.large,
        colors = cardColors,
        border = borderStroke,
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(modifier = Modifier.padding(AppSpacing.lg)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                DomainIcon(
                    domain = session.domain,
                    isHighlighted = isHighlighted
                )
                Spacer(Modifier.width(AppSpacing.md))
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = session.goalTitle ?: session.domain.name,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold
                        )
                        session.tag?.let { tag ->
                            Spacer(Modifier.width(AppSpacing.sm))
                            TagChip(tag)
                        }
                    }
                    Text(
                        text = buildSessionSubtitle(session, isActive, elapsedMinutes),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                SessionActionButton(
                    status = session.status,
                    isActive = isActive,
                    onStart = onStart,
                    onComplete = onComplete,
                    onSkip = onSkip,
                    onRestart = onRestart
                )
            }

            session.reason?.let { reason ->
                Spacer(Modifier.height(AppSpacing.sm))
                Text(
                    text = reason,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (session.tag == SessionTag.HIGH_PRIORITY)
                        MaterialTheme.colorScheme.error
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp
                )
            }

            session.learnedMinutes?.let { learned ->
                if (learned != session.plannedMinutes) {
                    Text(
                        text = "Adjusted: ${learned}m (learned from your history)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.tertiary,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}

@Composable
private fun DomainIcon(
    domain: com.tutushubham.pokidex.core.domain.model.Domain,
    isHighlighted: Boolean,
    modifier: Modifier = Modifier
) {
    val bgColor = if (isHighlighted)
        MaterialTheme.colorScheme.primary
    else
        MaterialTheme.colorScheme.surfaceContainerHighest

    val textColor = if (isHighlighted)
        MaterialTheme.colorScheme.onPrimary
    else
        MaterialTheme.colorScheme.onSurfaceVariant

    Surface(
        modifier = modifier.size(AppSizes.iconXl - AppSpacing.xs),
        shape = AppShapes.medium,
        color = bgColor
    ) {
        val emoji = when (domain) {
            com.tutushubham.pokidex.core.domain.model.Domain.STUDIES -> "📚"
            com.tutushubham.pokidex.core.domain.model.Domain.FITNESS -> "💪"
            com.tutushubham.pokidex.core.domain.model.Domain.WORK -> "💼"
            com.tutushubham.pokidex.core.domain.model.Domain.HOBBY -> "🎯"
        }
        Text(
            text = emoji,
            modifier = Modifier.padding(AppSpacing.sm + AppSpacing.xs),
            fontSize = 20.sp
        )
    }
}

@Composable
private fun TagChip(tag: SessionTag, modifier: Modifier = Modifier) {
    val (text, bgColor, textColor) = when (tag) {
        SessionTag.HIGH_PRIORITY -> Triple(
            "HIGH PRIORITY",
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        SessionTag.RECOVERY -> Triple(
            "RECOVERY",
            MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.5f),
            MaterialTheme.colorScheme.onErrorContainer
        )
    }

    Surface(
        modifier = modifier,
        shape = AppShapes.small,
        color = bgColor
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs),
            style = MaterialTheme.typography.labelSmall,
            color = textColor,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 8.sp,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
private fun SessionActionButton(
    status: SessionStatus,
    isActive: Boolean,
    onStart: () -> Unit,
    onComplete: () -> Unit,
    onSkip: (SkipReason) -> Unit,
    onRestart: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    when (status) {
        SessionStatus.PLANNED -> {
            Button(
                onClick = onStart,
                modifier = modifier,
                shape = AppShapes.pill,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                )
            ) {
                Text("Start", fontWeight = FontWeight.SemiBold)
            }
        }
        SessionStatus.IN_PROGRESS -> {
            Row {
                Button(
                    onClick = onComplete,
                    shape = AppShapes.pill
                ) {
                    Text("Done", fontWeight = FontWeight.SemiBold)
                }
                Spacer(Modifier.width(AppSpacing.xs + AppSpacing.xs))
                OutlinedButton(
                    onClick = { onSkip(SkipReason.LOW_ENERGY) },
                    shape = AppShapes.pill
                ) {
                    Text("Skip")
                }
            }
        }
        SessionStatus.COMPLETED -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Done ✓",
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(Modifier.height(AppSpacing.xs))
                OutlinedButton(
                    onClick = onRestart,
                    shape = AppShapes.pill
                ) {
                    Text("Restart", fontSize = 11.sp)
                }
            }
        }
        SessionStatus.SKIPPED -> {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Skipped",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Medium,
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(Modifier.height(AppSpacing.xs))
                OutlinedButton(
                    onClick = onRestart,
                    shape = AppShapes.pill
                ) {
                    Text("Restart", fontSize = 11.sp)
                }
            }
        }
    }
}

private fun buildSessionSubtitle(
    session: SessionUiModel,
    isActive: Boolean,
    elapsed: Int
): String {
    val domain = session.domain.name.lowercase().replaceFirstChar { it.uppercase() }
    val minutes = "${session.plannedMinutes} mins"
    val statusText = when {
        isActive -> "Active • ${elapsed}m elapsed"
        session.status == SessionStatus.COMPLETED -> "Completed"
        session.status == SessionStatus.SKIPPED -> "Skipped"
        else -> "Pending"
    }
    return "$domain • $minutes • $statusText"
}

@Preview(showBackground = true)
@Composable
private fun SessionSectionPreview() {
    PokidexTheme {
        SessionSection(
            sessions = PreviewData.sessionUiModels,
            activeSessionId = "s2",
            elapsedMinutes = 15,
            onStart = {}, onSkip = { _, _ -> }, onComplete = { _, _ -> }
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SessionCardHighPriorityPreview() {
    PokidexTheme {
        SessionCard(
            session = PreviewData.sessionUiModels[0],
            isActive = false, elapsedMinutes = 0,
            onStart = {}, onSkip = {}, onComplete = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SessionCardActivePreview() {
    PokidexTheme {
        SessionCard(
            session = PreviewData.sessionUiModels[1],
            isActive = true, elapsedMinutes = 15,
            onStart = {}, onSkip = {}, onComplete = {}
        )
    }
}
