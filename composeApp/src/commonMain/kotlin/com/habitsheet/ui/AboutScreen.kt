package com.habitsheet.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.habitsheet.presentation.VersionProvider

@Composable
fun AboutScreen(
    versionProvider: VersionProvider,
    onBack: () -> Unit,
    showBack: Boolean = true,
) {
    Scaffold(
        topBar = {
            SettingsTopBar(onBack, "About", showBack = true, insetTop = showBack)
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "Habit Tracker",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 32.dp)
            )
            
            Text(
                "Version ${versionProvider.versionName}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp)
            )

            Text(
                "Simple, offline-first habit tracking.",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 48.dp)
            )

            Text(
                "Your habit data is stored locally on your device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp)
            )

            Text(
                "Your data",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 48.dp).align(Alignment.Start)
            )
            
            Text(
                "Your habits and completion history are stored locally on your device. The app does not require an account or an internet connection to track your habits.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp).align(Alignment.Start)
            )

            Text(
                "Your habit data stays on your device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 16.dp).align(Alignment.Start)
            )
            
            Spacer(Modifier.height(64.dp))
        }
    }
}
