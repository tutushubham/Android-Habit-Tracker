package com.tutushubham.pokidex.feature_today.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tutushubham.pokidex.ui.theme.AppShapes
import com.tutushubham.pokidex.ui.theme.AppSpacing
import com.tutushubham.pokidex.ui.theme.PokidexTheme

@Composable
fun EmptyCard(
    title: String,
    message: String,
    buttonText: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(AppSpacing.xxl),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = AppShapes.large,
            tonalElevation = 3.dp
        ) {
            Column(
                modifier = Modifier.padding(AppSpacing.xxl),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(AppSpacing.sm))
                Text(message, style = MaterialTheme.typography.bodyMedium)
                Spacer(Modifier.height(AppSpacing.lg))
                Button(onClick = onClick) {
                    Text(buttonText)
                }
            }
        }
    }
}

@Composable
fun LoadingView(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        CircularProgressIndicator()
    }
}

@Preview(showBackground = true, heightDp = 300)
@Composable
private fun EmptyCardPreview() {
    PokidexTheme {
        EmptyCard(
            title = "Let's set up your system",
            message = "Complete onboarding to start planning.",
            buttonText = "Start setup",
            onClick = {}
        )
    }
}

@Preview(showBackground = true, heightDp = 200)
@Composable
private fun LoadingViewPreview() {
    PokidexTheme {
        LoadingView()
    }
}
