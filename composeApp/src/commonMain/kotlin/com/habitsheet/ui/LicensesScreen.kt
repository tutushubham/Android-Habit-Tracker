package com.habitsheet.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.unit.dp
import com.habitsheet.config.OpenSourceNotice
import com.habitsheet.config.openSourceNotices
import com.habitsheet.resources.*
import org.jetbrains.compose.resources.stringResource

@Composable
fun LicensesScreen(onBack: () -> Unit, showBack: Boolean = true, notices: List<OpenSourceNotice> = openSourceNotices) {
    val uriHandler = LocalUriHandler.current
    Scaffold(
        topBar = { SettingsTopBar(onBack, stringResource(Res.string.licenses_title), showBack = true, insetTop = showBack) },
        containerColor = MaterialTheme.colorScheme.background,
    ) { innerPadding ->
        Column(Modifier.fillMaxSize().padding(innerPadding).verticalScroll(rememberScrollState())) {
            Text(
                stringResource(Res.string.licenses_intro),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 16.dp),
            )
            notices.forEach { notice ->
                SettingsActionItem(notice.name, notice.license, onClick = { uriHandler.openUri(notice.url) })
            }
        }
    }
}
