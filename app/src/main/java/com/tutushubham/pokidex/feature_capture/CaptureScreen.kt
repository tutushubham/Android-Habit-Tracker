package com.tutushubham.pokidex.feature_capture

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.tutushubham.pokidex.core.domain.entity.Capture
import com.tutushubham.pokidex.ui.components.ShimmerLoadingView
import com.tutushubham.pokidex.ui.components.SyncErrorView
import com.tutushubham.pokidex.ui.theme.AppShapes
import com.tutushubham.pokidex.ui.theme.AppSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CaptureScreen(
    viewModel: CaptureViewModel,
    onNavigateToGoalEdit: (String) -> Unit = {}
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(Unit) {
        viewModel.onEvent(CaptureContract.CaptureEvent.ScreenOpened)
    }

    LaunchedEffect(Unit) {
        viewModel.effect.collect { effect ->
            when (effect) {
                is CaptureContract.CaptureEffect.ShowMessage -> {}
                is CaptureContract.CaptureEffect.NavigateToGoalEdit ->
                    onNavigateToGoalEdit(effect.intentId)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "INCOMING THOUGHTS",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text("Captured Thoughts", fontWeight = FontWeight.Bold)
                    }
                }
            )
        }
    ) { padding ->
        when {
            state.isLoading -> ShimmerLoadingView(modifier = Modifier.padding(padding))
            state.error != null -> SyncErrorView(
                onRetry = { viewModel.onEvent(CaptureContract.CaptureEvent.ScreenOpened) },
                modifier = Modifier.padding(padding)
            )
            else -> CaptureContent(
                state = state,
                onAdd = { text -> viewModel.onEvent(CaptureContract.CaptureEvent.AddCapture(text)) },
                onConvert = { id -> viewModel.onEvent(CaptureContract.CaptureEvent.ConvertToGoal(id)) },
                onDelete = { id -> viewModel.onEvent(CaptureContract.CaptureEvent.DeleteCapture(id)) },
                modifier = Modifier.padding(padding)
            )
        }
    }
}

@Composable
private fun CaptureContent(
    state: CaptureContract.CaptureState,
    onAdd: (String) -> Unit,
    onConvert: (String) -> Unit,
    onDelete: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var inputText by remember { mutableStateOf("") }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = AppSpacing.lg)
    ) {
        Spacer(modifier = Modifier.height(AppSpacing.md))
        Text(
            text = "Quickly capture ideas to structure them later.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(AppSpacing.lg))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)
        ) {
            OutlinedTextField(
                value = inputText,
                onValueChange = { inputText = it },
                modifier = Modifier.weight(1f),
                placeholder = { Text("What do you want to do?") },
                shape = AppShapes.medium,
                singleLine = true
            )
            Button(
                onClick = {
                    if (inputText.isNotBlank()) {
                        onAdd(inputText.trim())
                        inputText = ""
                    }
                },
                shape = AppShapes.medium
            ) {
                Text("Add")
            }
        }

        Spacer(modifier = Modifier.height(AppSpacing.xxl))

        LazyColumn(
            verticalArrangement = Arrangement.spacedBy(AppSpacing.md),
            modifier = Modifier.weight(1f)
        ) {
            items(
                items = state.captures,
                key = { it.id }
            ) { capture ->
                CaptureItem(
                    capture = capture,
                    onConvert = { onConvert(capture.id) },
                    onDelete = { onDelete(capture.id) }
                )
            }
        }

        if (state.processedCount > 0) {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = AppShapes.medium,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(
                    modifier = Modifier.padding(AppSpacing.lg),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Weekly Momentum",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Text(
                        text = "${state.processedCount} processed",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
            Spacer(modifier = Modifier.height(AppSpacing.lg))
        }
    }
}

@Composable
private fun CaptureItem(
    capture: Capture,
    onConvert: () -> Unit,
    onDelete: () -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = AppShapes.medium,
        tonalElevation = AppSpacing.xs
    ) {
        Column(modifier = Modifier.padding(AppSpacing.lg)) {
            Text(text = capture.content, style = MaterialTheme.typography.bodyMedium)
            Spacer(modifier = Modifier.height(AppSpacing.md))
            Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
                OutlinedButton(
                    onClick = onConvert,
                    shape = AppShapes.small
                ) {
                    Text("Turn into goal", style = MaterialTheme.typography.labelSmall)
                }
                OutlinedButton(
                    onClick = onDelete,
                    shape = AppShapes.small
                ) {
                    Text("Delete", style = MaterialTheme.typography.labelSmall)
                }
            }
        }
    }
}
