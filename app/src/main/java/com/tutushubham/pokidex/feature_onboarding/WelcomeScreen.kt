package com.tutushubham.pokidex.feature_onboarding

import android.content.res.Configuration
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
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
import com.tutushubham.pokidex.ui.theme.PokidexTheme

@Composable
fun WelcomeScreen(
    onNext: () -> Unit,
    onSignIn: () -> Unit = {}
) {
    val colorScheme = MaterialTheme.colorScheme
    val primaryDim = lerp(colorScheme.primary, Color.Black, 0.35f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.surface)
    ) {
        // Atmospheric blur — top-end primary glow
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 60.dp, y = (-40).dp)
                .size(250.dp)
                .alpha(0.07f)
                .blur(100.dp)
                .background(colorScheme.primary, CircleShape)
        )
        // Atmospheric blur — bottom-start secondary glow
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
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Branding anchor
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 24.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(colorScheme.primary, CircleShape)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "THE SANCTUARY",
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 3.sp
                    ),
                    color = colorScheme.onSurfaceVariant
                )
            }

            // Hero illustration — abstract stacked stones
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(colorScheme.surfaceContainer),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .width(80.dp)
                            .height(40.dp)
                            .alpha(0.8f)
                            .background(colorScheme.surfaceBright, RoundedCornerShape(50))
                    )
                    Box(
                        modifier = Modifier
                            .width(120.dp)
                            .height(56.dp)
                            .background(
                                colorScheme.surfaceContainerHighest,
                                RoundedCornerShape(50)
                            )
                    )
                    Box(
                        modifier = Modifier
                            .width(160.dp)
                            .height(72.dp)
                            .background(
                                colorScheme.surfaceContainerHigh,
                                RoundedCornerShape(50)
                            )
                    )
                }
            }

            Spacer(Modifier.height(40.dp))

            Text(
                text = "Build consistency without overthinking your day.",
                style = MaterialTheme.typography.headlineLarge.copy(
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 40.sp
                ),
                textAlign = TextAlign.Center,
                color = colorScheme.onSurface
            )

            Spacer(Modifier.height(16.dp))

            Text(
                text = "A calm space designed to help you nurture your daily rhythms with intention, not pressure.",
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                color = colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp)
            )

            Spacer(Modifier.weight(1f))

            // Page indicator dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.padding(bottom = 16.dp)
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

            // Primary CTA — gradient button
            Button(
                onClick = onNext,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(50),
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
                        Spacer(Modifier.width(12.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                            contentDescription = null,
                            tint = colorScheme.onPrimary
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Secondary action
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
    PokidexTheme(darkTheme = true) {
        WelcomeScreen(onNext = {})
    }
}
