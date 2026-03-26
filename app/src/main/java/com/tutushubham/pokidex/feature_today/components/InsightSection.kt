package com.tutushubham.pokidex.feature_today.components

import androidx.compose.foundation.clickable
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.tooling.preview.Preview
import com.tutushubham.pokidex.feature_today.InsightType
import com.tutushubham.pokidex.feature_today.InsightUiModel
import com.tutushubham.pokidex.feature_today.PreviewData
import com.tutushubham.pokidex.ui.theme.AppShapes
import com.tutushubham.pokidex.ui.theme.AppSpacing
import com.tutushubham.pokidex.ui.theme.PokidexTheme

@Composable
fun InsightSection(
    insights: List<InsightUiModel>,
    onNavigateToRecommendation: (String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    if (insights.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.xl),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.md)
    ) {
        insights.forEach { insight ->
            InsightCard(
                insight = insight,
                onClickRecommendation = {
                    val id = insight.relatedIntentId ?: return@InsightCard
                    if (insight.type == InsightType.BEHIND || insight.type == InsightType.RECOMMENDATION) {
                        onNavigateToRecommendation(id)
                    }
                }
            )
        }
    }
}

@Composable
private fun InsightCard(
    insight: InsightUiModel,
    onClickRecommendation: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val openRecommendation = insight.relatedIntentId != null &&
        (insight.type == InsightType.BEHIND || insight.type == InsightType.RECOMMENDATION)
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (openRecommendation) Modifier.clickable(onClick = onClickRecommendation)
                else Modifier
            ),
        shape = AppShapes.medium,
        color = insight.containerColor,
        shadowElevation = AppSpacing.xs
    ) {
        Column(
            modifier = Modifier.padding(AppSpacing.lg)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = insightIcon(insight.type),
                    style = MaterialTheme.typography.titleMedium,
                    color = insight.contentColor
                )
                Text(
                    text = insightLabel(insight.type),
                    style = MaterialTheme.typography.labelSmall,
                    color = insight.contentColor.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Bold,
                    fontSize = 10.sp,
                    letterSpacing = 1.sp
                )
            }

            Spacer(Modifier.height(AppSpacing.md))

            Text(
                text = insight.title,
                style = MaterialTheme.typography.titleSmall,
                color = insight.contentColor,
                fontWeight = FontWeight.Bold,
                lineHeight = 20.sp
            )

            Spacer(Modifier.height(AppSpacing.xs))

            Text(
                text = insight.subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = insight.contentColor.copy(alpha = 0.85f),
                fontSize = 12.sp
            )
        }
    }
}

private fun insightLabel(type: InsightType): String = when (type) {
    InsightType.BEHIND -> "URGENT"
    InsightType.FATIGUE -> "ATTENTION"
    InsightType.MOMENTUM -> "MOMENTUM"
    InsightType.RECOMMENDATION -> "INFO"
}

private fun insightIcon(type: InsightType): String = when (type) {
    InsightType.BEHIND -> "⚠️"
    InsightType.FATIGUE -> "📉"
    InsightType.MOMENTUM -> "🔥"
    InsightType.RECOMMENDATION -> "💡"
}

@Preview(showBackground = true)
@Composable
private fun InsightSectionPreview() {
    PokidexTheme {
        InsightSection(insights = PreviewData.insights)
    }
}
