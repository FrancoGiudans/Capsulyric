package com.example.islandlyrics.ui.miuix

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import top.yukonga.miuix.kmp.theme.MiuixTheme

private val MiuixTopBarLight = Color(0xFFF7F7F8)
private val MiuixTopBarDark = Color(0xFF18191A)

@Composable
internal fun neutralMiuixTopBarColor(): Color {
    val isDarkTheme = MiuixTheme.colorScheme.onSurface.luminance() > 0.5f
    return if (isDarkTheme) MiuixTopBarDark else MiuixTopBarLight
}
