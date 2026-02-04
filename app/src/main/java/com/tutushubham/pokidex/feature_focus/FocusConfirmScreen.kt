package com.tutushubham.pokidex.feature_focus

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.tutushubham.pokidex.ui.theme.PokidexTheme

@Composable
fun FocusConfirmScreen(
    preview: List<String>,
    onConfirm: () -> Unit
) {
    Column(Modifier.padding(16.dp)) {

        Text(
            text = "This is how it will work:",
            style = MaterialTheme.typography.titleLarge
        )

        Spacer(Modifier.height(12.dp))

        preview.forEachIndexed { index, name ->
            Text("Day ${index + 1}: $name")
        }

        Spacer(Modifier.height(24.dp))

        Button(onClick = onConfirm) {
            Text("Confirm")
        }
    }
}

@Preview(showBackground = true)
@Composable
private fun FocusConfirmScreenPreview() {
    PokidexTheme {
        FocusConfirmScreen(
            preview = listOf("Running", "Yoga", "Running", "Swimming", "Yoga"),
            onConfirm = {}
        )
    }
}

@Preview(showBackground = true)
@Composable
private fun FocusConfirmScreenShortPreview() {
    PokidexTheme {
        FocusConfirmScreen(
            preview = listOf("DSA", "Android"),
            onConfirm = {}
        )
    }
}
