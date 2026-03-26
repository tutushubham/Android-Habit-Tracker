package com.tutushubham.pokidex.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tutushubham.pokidex.core.engine.InsightExplanation
import com.tutushubham.pokidex.ui.theme.AppShapes
import com.tutushubham.pokidex.ui.theme.AppSpacing

@Composable
fun ExplanationButton(
    explanation: InsightExplanation?,
    modifier: Modifier = Modifier
) {
    if (explanation == null) return

    var expanded by remember { mutableStateOf(false) }

    Column(modifier = modifier) {
        Icon(
            imageVector = Icons.Outlined.Info,
            contentDescription = "Why?",
            modifier = Modifier
                .size(18.dp)
                .clickable { expanded = !expanded },
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )

        AnimatedVisibility(visible = expanded) {
            ExplanationContent(explanation = explanation)
        }
    }
}

@Composable
fun ExplanationContent(
    explanation: InsightExplanation,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = AppSpacing.sm),
        shape = AppShapes.small,
        tonalElevation = AppSpacing.xs
    ) {
        Column(modifier = Modifier.padding(AppSpacing.md)) {
            Text(
                text = explanation.title,
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(AppSpacing.xs))
            Text(
                text = explanation.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            if (explanation.factors.isNotEmpty()) {
                Spacer(modifier = Modifier.height(AppSpacing.sm))
                explanation.factors.forEach { factor ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = AppSpacing.xs / 2)
                    ) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(AppSpacing.sm))
                        Text(
                            text = factor,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
