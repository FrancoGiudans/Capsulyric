package com.example.islandlyrics.ui.theme.material

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarColors
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

private val MaterialTopBarLight = Color(0xFFF7F7F8)
private val MaterialTopBarDark = Color(0xFF18191A)
private val MaterialTopBarContentLight = Color(0xFF191C1D)
private val MaterialTopBarContentDark = Color(0xFFF1F0F4)

@Composable
fun neutralMaterialTopBarColors(): TopAppBarColors {
    val isDarkTheme = MaterialTheme.colorScheme.onSurface.luminance() > 0.5f
    val containerColor = if (isDarkTheme) MaterialTopBarDark else MaterialTopBarLight
    val contentColor = if (isDarkTheme) MaterialTopBarContentDark else MaterialTopBarContentLight
    return TopAppBarDefaults.topAppBarColors(
        containerColor = containerColor,
        scrolledContainerColor = containerColor,
        titleContentColor = contentColor,
        navigationIconContentColor = contentColor,
        actionIconContentColor = contentColor
    )
}
