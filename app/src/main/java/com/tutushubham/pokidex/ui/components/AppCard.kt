package com.tutushubham.pokidex.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import com.tutushubham.pokidex.ui.theme.AppShapes
import com.tutushubham.pokidex.ui.theme.AppSpacing

@Composable
fun AppCard(
    modifier: Modifier = Modifier,
    shape: Shape = AppShapes.large,
    containerColor: Color = MaterialTheme.colorScheme.surfaceContainerLow,
    border: BorderStroke? = null,
    onClick: (() -> Unit)? = null,
    contentPadding: Modifier = Modifier.padding(AppSpacing.lg),
    content: @Composable ColumnScope.() -> Unit
) {
    if (onClick != null) {
        Surface(
            onClick = onClick,
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = containerColor,
            tonalElevation = 0.dp,
            border = border
        ) {
            Column(modifier = contentPadding, content = content)
        }
    } else {
        Surface(
            modifier = modifier.fillMaxWidth(),
            shape = shape,
            color = containerColor,
            tonalElevation = 0.dp,
            border = border
        ) {
            Column(modifier = contentPadding, content = content)
        }
    }
}
