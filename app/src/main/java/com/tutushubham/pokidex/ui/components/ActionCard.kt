package com.tutushubham.pokidex.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.tutushubham.pokidex.ui.theme.AppShapes
import com.tutushubham.pokidex.ui.theme.AppSpacing

@Composable
fun ActionCard(
    icon: String,
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    primaryActionLabel: String? = null,
    onPrimaryAction: (() -> Unit)? = null,
    secondaryActionLabel: String? = null,
    onSecondaryAction: (() -> Unit)? = null
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = AppShapes.medium,
        tonalElevation = AppSpacing.xs
    ) {
        Column(modifier = Modifier.padding(AppSpacing.lg)) {
            Row(verticalAlignment = Alignment.Top) {
                Text(icon, style = MaterialTheme.typography.titleLarge)
                Spacer(modifier = Modifier.width(AppSpacing.md))
                Column {
                    Text(text = title, style = MaterialTheme.typography.titleSmall)
                    Spacer(modifier = Modifier.height(AppSpacing.xs))
                    Text(
                        text = message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            if (primaryActionLabel != null || secondaryActionLabel != null) {
                Spacer(modifier = Modifier.height(AppSpacing.lg))
                Row {
                    if (primaryActionLabel != null && onPrimaryAction != null) {
                        Button(
                            onClick = onPrimaryAction,
                            shape = AppShapes.medium
                        ) {
                            Text(primaryActionLabel)
                        }
                    }
                    if (secondaryActionLabel != null && onSecondaryAction != null) {
                        Spacer(modifier = Modifier.width(AppSpacing.sm))
                        OutlinedButton(
                            onClick = onSecondaryAction,
                            shape = AppShapes.medium
                        ) {
                            Text(secondaryActionLabel)
                        }
                    }
                }
            }
        }
    }
}
