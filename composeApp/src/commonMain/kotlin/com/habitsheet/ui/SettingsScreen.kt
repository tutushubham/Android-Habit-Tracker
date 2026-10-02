package com.habitsheet.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.habitsheet.presentation.SettingsViewModel
import com.habitsheet.presentation.ThemeMode

@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onBack: () -> Unit,
    onManageCategories: () -> Unit,
    onBackup: () -> Unit,
    onAbout: () -> Unit,
    showBack: Boolean = true,
) {
    val themeMode by viewModel.themeMode.collectAsState()
    val sheetUrl by viewModel.sheetUrl.collectAsState()
    val sheetMessage by viewModel.sheetMessage.collectAsState()
    val syncState by viewModel.sheetSyncState.collectAsState()
    var sheetDraft by remember(sheetUrl) { mutableStateOf(sheetUrl) }

    Scaffold(
        topBar = {
            SettingsTopBar(onBack, showBack = showBack)
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        Box(Modifier.fillMaxSize().padding(innerPadding), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
        ) {
            SettingsSection("Plan sync") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp)) {
                    OutlinedTextField(
                        value = sheetDraft,
                        onValueChange = { sheetDraft = it },
                        label = { Text("Spreadsheet link") },
                        placeholder = { Text("https://docs.google.com/spreadsheets/d/…") },
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 2,
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                        OutlinedButton(onClick = { viewModel.saveSheetUrl(sheetDraft) }) { Text("Save link") }
                        Button(onClick = viewModel::syncNow, enabled = sheetUrl.isNotBlank() && !syncState.busy) {
                            Text(if (syncState.busy) "Syncing…" else "Connect & sync")
                        }
                    }
                    sheetMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                    Text(
                        syncState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        "The Sheet controls dated sessions. Checks for Plan rows work offline and upload when you reconnect. Other tabs are unchanged.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            SettingsSection("Appearance") {
                ThemeOption("System Default", ThemeMode.System, themeMode) { viewModel.setThemeMode(it) }
                ThemeOption("Light", ThemeMode.Light, themeMode) { viewModel.setThemeMode(it) }
                ThemeOption("Dark", ThemeMode.Dark, themeMode) { viewModel.setThemeMode(it) }
            }

            SettingsSection("Management") {
                SettingsActionItem("Manage Categories", "Add, edit, or remove habit categories", onManageCategories)
            }

            SettingsSection("Data") {
                SettingsActionItem("Data & Backup", "Export or import your habit history", onBackup)
            }

            SettingsSection("About") {
                SettingsActionItem("About Habit Sheet", "App information and privacy", onAbout)
            }
            
            Spacer(Modifier.height(48.dp))
        }
        }
    }
}

@Composable
private fun SettingsSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.padding(top = 24.dp)) {
        SectionLabel(title, Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
        content()
    }
}

@Composable
private fun ThemeOption(label: String, mode: ThemeMode, current: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(
                onClick = { onSelect(mode) },
                onClickLabel = "Select $label theme"
            )
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
        RadioButton(
            selected = current == mode, 
            onClick = null,
            modifier = Modifier.semantics { role = Role.RadioButton }
        )
    }
}

@Composable
private fun SettingsActionItem(title: String, description: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(
                onClick = onClick,
                role = Role.Button
            )
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            "›", 
            fontSize = 24.sp, 
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.semantics { contentDescription = "Navigate" }
        )
    }
}

@Composable
internal fun SettingsTopBar(
    onBack: () -> Unit,
    title: String = "Settings",
    showBack: Boolean = true,
    insetTop: Boolean = showBack,
) {
    val borderColor = MaterialTheme.colorScheme.outlineVariant
    Row(
        Modifier
            .fillMaxWidth()
            .then(if (insetTop) Modifier.windowInsetsPadding(WindowInsets.statusBars) else Modifier)
            .height(if (showBack) 64.dp else 72.dp)
            .drawBehind {
                drawLine(borderColor, Offset(0f, size.height), Offset(size.width, size.height), strokeWidth = 1.dp.toPx())
            }
            .padding(horizontal = if (showBack) 8.dp else 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (showBack) IconButton(onClick = onBack) {
            Text(
                "‹", 
                fontSize = 32.sp, 
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.semantics { contentDescription = "Back" }
            )
        }
        Text(title, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(start = if (showBack) 8.dp else 0.dp))
    }
}
