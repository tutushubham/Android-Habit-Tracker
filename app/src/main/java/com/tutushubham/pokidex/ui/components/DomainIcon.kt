package com.tutushubham.pokidex.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.ui.theme.AppShapes
import com.tutushubham.pokidex.ui.theme.AppSizes

@Composable
fun DomainIcon(
    domain: Domain,
    modifier: Modifier = Modifier,
    size: Dp = AppSizes.iconXl
) {
    Box(
        modifier = modifier
            .size(size)
            .background(
                MaterialTheme.colorScheme.primaryContainer,
                AppShapes.medium
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = domain.emoji,
            style = MaterialTheme.typography.titleMedium
        )
    }
}

private val Domain.emoji: String
    get() = when (this) {
        Domain.STUDIES -> "📚"
        Domain.FITNESS -> "💪"
        Domain.WORK -> "💼"
        Domain.HOBBY -> "🎨"
    }
