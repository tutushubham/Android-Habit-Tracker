package com.tutushubham.pokidex.feature_today.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.tutushubham.pokidex.feature_today.ActiveSessionUiModel
import com.tutushubham.pokidex.feature_today.ConfidenceLevel
import com.tutushubham.pokidex.feature_today.PreviewData
import com.tutushubham.pokidex.feature_today.SessionTag
import com.tutushubham.pokidex.ui.theme.AppShapes
import com.tutushubham.pokidex.ui.theme.AppSpacing
import com.tutushubham.pokidex.ui.theme.PokidexTheme

@Composable
fun SessionContext(
    model: ActiveSessionUiModel,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.xxl),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = sessionTypeLabel(model),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )

        Spacer(Modifier.height(AppSpacing.sm))

        Text(
            text = model.goalTitle,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center,
            lineHeight = 36.sp
        )

        Spacer(Modifier.height(AppSpacing.md))

        ConfidenceChip(
            confidence = model.confidence,
            label = model.confidenceLabel
        )

        model.reason?.let { reason ->
            Spacer(Modifier.height(AppSpacing.sm))
            Text(
                text = reason,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun ConfidenceChip(
    confidence: ConfidenceLevel,
    label: String,
    modifier: Modifier = Modifier
) {
    val icon = when (confidence) {
        ConfidenceLevel.HIGH -> "✨"
        ConfidenceLevel.MEDIUM -> "⚙️"
        ConfidenceLevel.LOW -> "🔍"
    }

    Surface(
        modifier = modifier,
        shape = AppShapes.extraLarge,
        color = MaterialTheme.colorScheme.surfaceContainerHigh
    ) {
        Row(
            modifier = Modifier.padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            Text(text = icon, fontSize = 12.sp)
            Text(
                text = label.uppercase(),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.5.sp,
                fontSize = 10.sp
            )
        }
    }
}

private fun sessionTypeLabel(model: ActiveSessionUiModel): String = when (model.tag) {
    SessionTag.HIGH_PRIORITY -> "CRITICAL RECOVERY SESSION"
    SessionTag.RECOVERY -> "RECOVERY SESSION"
    else -> "DEEP FOCUS SESSION"
}

@Preview(showBackground = true)
@Composable
private fun SessionContextDeepFocusPreview() {
    PokidexTheme {
        SessionContext(model = PreviewData.activeSessionOnTrack)
    }
}

@Preview(showBackground = true)
@Composable
private fun SessionContextCriticalPreview() {
    PokidexTheme {
        SessionContext(model = PreviewData.activeSessionCritical)
    }
}
