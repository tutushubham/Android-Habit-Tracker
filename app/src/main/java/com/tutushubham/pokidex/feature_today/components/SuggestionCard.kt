package com.tutushubham.pokidex.feature_today.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.tutushubham.pokidex.feature_today.PreviewData
import com.tutushubham.pokidex.feature_today.SessionSuggestion
import com.tutushubham.pokidex.ui.theme.AppShapes
import com.tutushubham.pokidex.ui.theme.AppSpacing
import com.tutushubham.pokidex.ui.theme.PokidexTheme

@Composable
fun SuggestionCard(
    suggestion: SessionSuggestion,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.xxl),
        shape = AppShapes.large,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
        tonalElevation = 2.dp
    ) {
        Row(
            modifier = Modifier.padding(AppSpacing.lg),
            verticalAlignment = Alignment.Top
        ) {
            Surface(
                shape = AppShapes.medium,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
            ) {
                Text(
                    text = suggestion.icon,
                    modifier = Modifier.padding(AppSpacing.sm + AppSpacing.xs),
                    fontSize = 20.sp
                )
            }

            Spacer(Modifier.width(AppSpacing.md))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = suggestion.title,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp
                )
                Text(
                    text = suggestion.message,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    lineHeight = 16.sp
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun SuggestionCardPreview() {
    PokidexTheme {
        SuggestionCard(suggestion = PreviewData.suggestion)
    }
}

@Preview(showBackground = true)
@Composable
private fun SuggestionCardOvertimePreview() {
    PokidexTheme {
        SuggestionCard(
            suggestion = SessionSuggestion(
                title = "Session Overtime",
                message = "You've exceeded the planned 45m. Wrap up or adjust your estimate.",
                icon = "⏱️"
            )
        )
    }
}
