package com.habitsheet.ui.month

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.habitsheet.domain.model.MonthKey
import com.habitsheet.ui.HabitIcon
import com.habitsheet.ui.HabitIconGlyph
import com.habitsheet.ui.HabitSheetTheme

@Composable
internal fun MonthYearPickerDialog(
    current: MonthKey,
    onDismiss: () -> Unit,
    onSelect: (MonthKey) -> Unit
) {
    var year by remember { mutableStateOf(current.year) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.Center, modifier = Modifier.fillMaxWidth()) {
                IconButton(onClick = { year-- }, modifier = Modifier.semantics { contentDescription = "Previous year" }) {
                    HabitIcon(HabitIconGlyph.Back, Modifier.size(20.dp))
                }
                Text(year.toString(), style = MaterialTheme.typography.titleLarge, modifier = Modifier.padding(horizontal = 16.dp))
                IconButton(onClick = { year++ }, modifier = Modifier.semantics { contentDescription = "Next year" }) {
                    HabitIcon(HabitIconGlyph.Forward, Modifier.size(20.dp))
                }
            }
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                val months = listOf(
                    "January", "February", "March", "April", "May", "June",
                    "July", "August", "September", "October", "November", "December"
                )
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    months.forEachIndexed { index, name ->
                        val m = index + 1
                        val selected = current.year == year && current.month == m
                        FilterChip(
                            selected = selected,
                            onClick = { onSelect(MonthKey(year, m)) },
                            label = { Text(name) }
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Preview
@Composable
private fun MonthYearPickerDialogPreview() {
    HabitSheetTheme(darkTheme = false) {
        MonthYearPickerDialog(current = MonthKey(2026, 10), onDismiss = {}, onSelect = {})
    }
}
