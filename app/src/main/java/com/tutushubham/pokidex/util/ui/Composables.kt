package com.tutushubham.pokidex.util.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp

@Composable
fun ColumnWithBackgroundImage(
    backgroundResId: Int,
    content: @Composable (ColumnScope) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        Image(
            painter = painterResource(id = backgroundResId),
            contentDescription = "Background Image", // Optional: Provide a description for accessibility
            modifier = Modifier
                .fillMaxSize()
                .alpha(0.25f), // Make the image fill the entire screen
            contentScale = ContentScale.Crop // Adjust as needed (e.g., Cover, Fit)
        )
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp), // Add padding for content
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            content(this) // Add the content of the column
        }
    }
}