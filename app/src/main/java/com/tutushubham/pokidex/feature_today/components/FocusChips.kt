package com.tutushubham.pokidex.feature_today.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.tooling.preview.Preview
import com.tutushubham.pokidex.core.domain.entity.Focus
import com.tutushubham.pokidex.core.domain.model.Domain
import com.tutushubham.pokidex.feature_today.PreviewData
import com.tutushubham.pokidex.ui.theme.PokidexTheme

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun FocusChips(
    focusMap: Map<Domain, Focus>,
    onChangeFocus: (Domain) -> Unit,
    modifier: Modifier = Modifier
) {
    if (focusMap.isEmpty()) return

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "ACTIVE FOCUS DOMAINS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = FontWeight.SemiBold
            )
            TextButton(onClick = { focusMap.keys.firstOrNull()?.let(onChangeFocus) }) {
                Text("Change focus")
            }
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            focusMap.forEach { (domain, focus) ->
                AssistChip(
                    onClick = { onChangeFocus(domain) },
                    label = { Text(focus.name) },
                    colors = AssistChipDefaults.assistChipColors(
                        containerColor = if (domain == focusMap.keys.first())
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.secondaryContainer,
                        labelColor = if (domain == focusMap.keys.first())
                            MaterialTheme.colorScheme.onPrimary
                        else
                            MaterialTheme.colorScheme.onSecondaryContainer
                    )
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FocusChipsPreview() {
    PokidexTheme {
        FocusChips(focusMap = PreviewData.focuses, onChangeFocus = {})
    }
}
