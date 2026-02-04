package com.tutushubham.pokidex.feature_focus

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ListItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tutushubham.pokidex.core.domain.entity.Focus
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.ui.theme.PokidexTheme
import java.time.LocalDate

@Composable
fun FocusListScreen(
    focuses: List<Focus>,
    onAdd: () -> Unit,
    onEdit: (String) -> Unit,
    onNext: (() -> Unit)? = null
) {
    LazyColumn(
        contentPadding = PaddingValues(16.dp)
    ) {
        items(focuses, key = { it.id }) { focus ->
            ListItem(
                headlineContent = { Text(focus.name) },
                supportingContent = {
                    focus.deadline?.let {
                        Text("Deadline: $it")
                    }
                },
                modifier = Modifier.clickable {
                    onEdit(focus.id)
                }
            )
        }

        item {
            Spacer(Modifier.height(16.dp))
            OutlinedButton(onClick = onAdd) {
                Text("+ Add Focus")
            }
            if (onNext != null) {
                Spacer(Modifier.height(8.dp))
                Button(onClick = onNext) {
                    Text("Next: Set strategy")
                }
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FocusListScreenPreview() {
    PokidexTheme {
        FocusListScreen(
            focuses = listOf(
                Focus("f1", Domain.FITNESS, "Running", 1, null),
                Focus("f2", Domain.FITNESS, "Yoga", 1, LocalDate.of(2025, 3, 1)),
                Focus("f3", Domain.FITNESS, "Swimming", 2, null)
            ),
            onAdd = {},
            onEdit = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FocusListScreenEmptyPreview() {
    PokidexTheme {
        FocusListScreen(
            focuses = emptyList(),
            onAdd = {},
            onEdit = {}
        )
    }
}
