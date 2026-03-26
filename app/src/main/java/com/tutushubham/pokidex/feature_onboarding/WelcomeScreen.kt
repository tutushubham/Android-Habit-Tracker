package com.tutushubham.pokidex.feature_onboarding

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tutushubham.pokidex.ui.theme.AppShapes
import com.tutushubham.pokidex.ui.theme.AppSizes
import com.tutushubham.pokidex.ui.theme.AppSpacing
import com.tutushubham.pokidex.ui.theme.PokidexTheme

@Composable
fun WelcomeScreen(
    onNext: () -> Unit,
    onSignIn: () -> Unit = {}
) {
    val colorScheme = MaterialTheme.colorScheme
    val primaryDim = lerp(colorScheme.primary, Color.Black, 0.35f)
    val scrollState = rememberScrollState()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.surface)
    ) {
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 60.dp, y = (-40).dp)
                .size(250.dp)
                .alpha(0.07f)
                .blur(100.dp)
                .background(colorScheme.primary, CircleShape)
        )
        Box(
            modifier = Modifier
                .align(Alignment.BottomStart)
                .offset(x = (-30).dp, y = 30.dp)
                .size(180.dp)
                .alpha(0.05f)
                .blur(80.dp)
                .background(colorScheme.secondary, CircleShape)
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = AppSpacing.xxl)
                .padding(top = AppSpacing.xxl, bottom = AppSpacing.xxxl),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = AppSpacing.xxl)
            ) {
                Box(
                    modifier = Modifier
                        .size(AppSpacing.sm)
                        .background(colorScheme.primary, CircleShape)
                )
                Spacer(Modifier.width(AppSpacing.sm))
                Text(
                    text = "THE SANCTUARY",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp
                    ),
                    color = colorScheme.onSurfaceVariant
                )
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Build consistency without overthinking your day.",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontWeight = FontWeight.ExtraBold,
                        lineHeight = 40.sp
                    ),
                    textAlign = TextAlign.Center,
                    color = colorScheme.onSurface
                )

                Spacer(Modifier.height(AppSpacing.lg))

                Text(
                    text = "A calm space designed to help you nurture your daily rhythms with intention, not pressure.",
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = AppSpacing.lg)
                )

                Spacer(Modifier.height(AppSpacing.xxxl))

                val features = listOf(
                    Triple("🧭", "Intelligent Planning", "Turn goals into doable sessions that fit your real schedule."),
                    Triple("🎯", "Focus System", "Block-level focus so each part of your day has a clear purpose."),
                    Triple("📈", "Adaptive Learning", "Your plan adjusts as you complete work and priorities shift."),
                    Triple("🌿", "Gentle accountability", "Progress without guilt — small wins, tracked quietly.")
                )

                features.forEach { (emoji, title, body) ->
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = AppShapes.medium,
                        tonalElevation = AppSpacing.xs,
                        color = colorScheme.surfaceContainerLow
                    ) {
                        Row(
                            modifier = Modifier.padding(AppSpacing.lg),
                            verticalAlignment = Alignment.Top
                        ) {
                            Text(emoji, style = MaterialTheme.typography.headlineSmall)
                            Spacer(Modifier.width(AppSpacing.md))
                            Column {
                                Text(
                                    text = title,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = colorScheme.onSurface
                                )
                                Spacer(Modifier.height(AppSpacing.xs))
                                Text(
                                    text = body,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(AppSpacing.md))
                }

                Spacer(Modifier.height(AppSpacing.lg))
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
                modifier = Modifier.padding(bottom = AppSpacing.lg)
            ) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .background(colorScheme.primary, CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .alpha(0.3f)
                        .background(colorScheme.secondary, CircleShape)
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .alpha(0.3f)
                        .background(colorScheme.tertiary, CircleShape)
                )
            }

            Button(
                onClick = onNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(AppSizes.buttonHeight),
                shape = AppShapes.pill,
                colors = ButtonDefaults.buttonColors(containerColor = Color.Transparent),
                contentPadding = PaddingValues(0.dp)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.horizontalGradient(
                                colors = listOf(primaryDim, colorScheme.primary)
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Get Started",
                            style = MaterialTheme.typography.titleMedium.copy(
                                fontWeight = FontWeight.Bold
                            ),
                            color = colorScheme.onPrimary
                        )
                        Spacer(Modifier.width(AppSpacing.md))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = colorScheme.onPrimary
                        )
                    }
                }
            }

            Spacer(Modifier.height(AppSpacing.lg))

            TextButton(onClick = onSignIn) {
                Text(
                    text = buildAnnotatedString {
                        append("Already have a sanctuary? ")
                        withStyle(
                            SpanStyle(
                                color = colorScheme.primary,
                                fontWeight = FontWeight.SemiBold
                            )
                        ) {
                            append("Sign In")
                        }
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Preview(showSystemUi = true, uiMode = Configuration.UI_MODE_NIGHT_NO)
@Composable
private fun WelcomeScreenLightPreview() {
    PokidexTheme {
        WelcomeScreen(onNext = {})
    }
}

@Preview(showSystemUi = true, uiMode = Configuration.UI_MODE_NIGHT_YES)
@Composable
private fun WelcomeScreenDarkPreview() {
    PokidexTheme(themeMode = com.tutushubham.pokidex.feature_settings.ThemeMode.DARK) {
        WelcomeScreen(onNext = {})
    }
}
