package com.habitsheet.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.habitsheet.resources.*
import org.jetbrains.compose.resources.stringResource

data class TutorialStep(
    val title: String,
    val description: String,
    val targetTag: String,
    val nextButtonText: String? = null,
)

@Composable
fun TutorialOverlay(
    steps: List<TutorialStep>,
    targetPositions: Map<String, Rect>,
    onComplete: () -> Unit,
    onSkip: () -> Unit,
) {
    var currentStepIndex by remember { mutableStateOf(0) }
    val currentStep = steps.getOrNull(currentStepIndex) ?: return
    val targetRect = targetPositions[currentStep.targetTag]

    val density = LocalDensity.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(100f)
            .background(Color.Black.copy(alpha = 0.7f))
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
            ) { /* Block clicks to underlying UI */ },
    ) {
        // Spotlight effect would be nice but simple highlight is required
        if (targetRect != null) {
            Box(
                modifier = Modifier
                    .offset(
                        x = with(density) { targetRect.left.toDp() } - 4.dp,
                        y = with(density) { targetRect.top.toDp() } - 4.dp,
                    )
                    .size(
                        width = with(density) { targetRect.width.toDp() } + 8.dp,
                        height = with(density) { targetRect.height.toDp() } + 8.dp,
                    )
                    .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp)),
            )
        }

        // Tooltip
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(32.dp)
                .widthIn(max = 400.dp)
                .background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp))
                .padding(24.dp),
        ) {
            Text(
                text = stringResource(Res.string.tutorial_step_of, currentStepIndex + 1, steps.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            Text(
                text = currentStep.title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 8.dp),
            )

            Text(
                text = currentStep.description,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(onClick = onSkip) {
                    val skipDescription = stringResource(Res.string.tutorial_skip_description)
                    Text(
                        stringResource(Res.string.tutorial_skip),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.semantics { contentDescription = skipDescription },
                    )
                }

                Button(
                    onClick = {
                        if (currentStepIndex < steps.size - 1) {
                            currentStepIndex++
                        } else {
                            onComplete()
                        }
                    },
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(currentStep.nextButtonText ?: stringResource(Res.string.tutorial_next))
                }
            }
        }
    }
}

fun Modifier.tutorialTarget(
    tag: String,
    onPositioned: (String, Rect) -> Unit,
) = this.onGloballyPositioned { coords ->
    val rect = Rect(coords.positionInRoot(), coords.size.toSize())
    onPositioned(tag, rect)
}

private fun IntSize.toSize() = androidx.compose.ui.geometry.Size(width.toFloat(), height.toFloat())
