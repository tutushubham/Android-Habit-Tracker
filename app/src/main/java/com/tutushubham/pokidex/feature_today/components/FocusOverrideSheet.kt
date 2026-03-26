package com.tutushubham.pokidex.feature_today.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.tutushubham.pokidex.core.domain.entity.Focus
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.ui.theme.AppSpacing
import com.tutushubham.pokidex.ui.theme.PokidexTheme
import java.time.LocalDate

@Composable
fun FocusOverrideSheet(
    focuses: List<Focus>,
    onSelect: (Focus) -> Unit,
    onDismiss: () -> Unit,
    onClearOverride: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier.padding(AppSpacing.lg)) {
        Text(
            text = "Switch focus just for today",
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(AppSpacing.lg))
        focuses.forEach { focus ->
            ListItem(
                headlineContent = { Text(focus.name) },
                modifier = Modifier.clickable {
                    onSelect(focus)
                    onDismiss()
                }
            )
        }
        Spacer(Modifier.height(AppSpacing.sm))
        TextButton(onClick = {
            onClearOverride()
            onDismiss()
        }) {
            Text("Use automatic focus")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FocusOverrideSheetPreview() {
    PokidexTheme {
        FocusOverrideSheet(
            focuses = listOf(
                Focus("f1", Domain.STUDIES, "DSA", 1, null),
                Focus("f2", Domain.STUDIES, "Android", 1, null),
                Focus("f3", Domain.STUDIES, "Guitar", 1, LocalDate.of(2030, 2, 1))
            ),
            onSelect = {}, onDismiss = {}, onClearOverride = {}
        )
    }
}
