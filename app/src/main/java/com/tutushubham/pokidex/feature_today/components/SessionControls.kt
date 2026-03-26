package com.tutushubham.pokidex.feature_today.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tutushubham.pokidex.ui.theme.AppSizes
import com.tutushubham.pokidex.ui.theme.AppSpacing
import com.tutushubham.pokidex.ui.theme.PokidexTheme

@Composable
fun SessionControls(
    onSkip: () -> Unit,
    onPauseResume: () -> Unit,
    onFinish: () -> Unit,
    isPaused: Boolean,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        ControlButton(
            label = "Skip",
            emoji = "⏭️",
            onClick = onSkip
        )

        Button(
            onClick = onPauseResume,
            modifier = Modifier.size(80.dp),
            shape = CircleShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            ),
            elevation = ButtonDefaults.buttonElevation(
                defaultElevation = 8.dp,
                pressedElevation = 2.dp
            )
        ) {
            Text(
                text = if (isPaused) "▶️" else "⏸️",
                fontSize = 28.sp
            )
        }

        ControlButton(
            label = "Finish",
            emoji = "✅",
            onClick = onFinish,
            isTertiary = true
        )
    }
}

@Composable
private fun ControlButton(
    label: String,
    emoji: String,
    onClick: () -> Unit,
    isTertiary: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        FilledTonalButton(
            onClick = onClick,
            modifier = Modifier.size(AppSizes.buttonHeight),
            shape = CircleShape,
            colors = if (isTertiary) {
                ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer,
                    contentColor = MaterialTheme.colorScheme.onTertiaryContainer
                )
            } else {
                ButtonDefaults.filledTonalButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    contentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        ) {
            Text(text = emoji, fontSize = 20.sp)
        }
        Spacer(Modifier.height(AppSpacing.xs + AppSpacing.xs))
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.Bold,
            fontSize = 9.sp,
            letterSpacing = 1.sp
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SessionControlsPlayingPreview() {
    PokidexTheme {
        SessionControls(
            onSkip = {}, onPauseResume = {}, onFinish = {},
            isPaused = false
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun SessionControlsPausedPreview() {
    PokidexTheme {
        SessionControls(
            onSkip = {}, onPauseResume = {}, onFinish = {},
            isPaused = true
        )
    }
}
