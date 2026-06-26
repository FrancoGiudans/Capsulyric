package com.example.islandlyrics.ui.miuix.navigation

import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.extended.Back
import top.yukonga.miuix.kmp.theme.MiuixTheme

@Composable
fun MiuixBackIcon(contentDescription: String?) {
    Icon(
        imageVector = MiuixIcons.Back,
        contentDescription = contentDescription,
        tint = MiuixTheme.colorScheme.onBackground
    )
}
