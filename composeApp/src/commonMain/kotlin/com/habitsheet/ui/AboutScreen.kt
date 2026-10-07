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
import com.habitsheet.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun AboutScreen(
    versionProvider: VersionProvider,
    onBack: () -> Unit,
    showBack: Boolean = true,
) {
    Scaffold(
        topBar = {
            SettingsTopBar(onBack, stringResource(Res.string.settings_about), showBack = true, insetTop = showBack)
        },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                stringResource(Res.string.about_title),
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(top = 32.dp),
            )

            Text(
                stringResource(Res.string.about_version, versionProvider.versionName),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )

            Text(
                stringResource(Res.string.about_tagline),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.padding(top = 48.dp),
            )

            Text(
                stringResource(Res.string.about_local),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp),
            )

            Text(
                stringResource(Res.string.about_data_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 48.dp).align(Alignment.Start),
            )

            Text(
                stringResource(Res.string.about_data_text),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 16.dp).align(Alignment.Start),
            )

            Text(
                stringResource(Res.string.about_stays),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 16.dp).align(Alignment.Start),
            )

            Spacer(Modifier.height(64.dp))
        }
    }
}
