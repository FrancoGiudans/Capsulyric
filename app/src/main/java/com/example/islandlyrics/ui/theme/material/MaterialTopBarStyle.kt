package com.example.islandlyrics.ui.theme.material

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable

// TopAppBar blends with the page background when the large title is expanded.
// When collapsed (scrolled), it picks up surfaceContainer for a subtle floating tint.
@Composable
fun neutralMaterialTopBarColors(): TopAppBarColors = TopAppBarDefaults.topAppBarColors(
    containerColor = MaterialTheme.colorScheme.background,
    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
)

@Composable
fun materialPageContainerColor() = MaterialTheme.colorScheme.background
