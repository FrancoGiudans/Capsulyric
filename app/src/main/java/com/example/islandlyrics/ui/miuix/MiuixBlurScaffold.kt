package com.example.islandlyrics.ui.miuix

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import top.yukonga.miuix.kmp.basic.FabPosition
import top.yukonga.miuix.kmp.basic.Scaffold
import top.yukonga.miuix.kmp.blur.layerBackdrop
import top.yukonga.miuix.kmp.blur.rememberLayerBackdrop
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * A wrapper for [Scaffold] that provides a [top.yukonga.miuix.kmp.blur.Backdrop]
 * for blur effects in [TopAppBar] and popups.
 */
@Composable
fun MiuixBlurScaffold(
    modifier: Modifier = Modifier,
    topBar: @Composable () -> Unit = {},
    bottomBar: @Composable () -> Unit = {},
    floatingActionButton: @Composable () -> Unit = {},
    floatingActionButtonPosition: FabPosition = FabPosition.End,
    popupHost: @Composable () -> Unit = { MiuixBlurPopupHost() },
    containerColor: Color = MiuixTheme.colorScheme.surface,
    contentWindowInsets: WindowInsets = WindowInsets.systemBars.union(WindowInsets.displayCutout),
    content: @Composable (PaddingValues) -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences("IslandLyricsPrefs", android.content.Context.MODE_PRIVATE)
    }
    var blurEnabled by remember(prefs) {
        mutableStateOf(prefs.getBoolean("card_blur_enabled", false))
    }
    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == "card_blur_enabled") {
                blurEnabled = prefs.getBoolean(key, false)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }
    val backdropBackground = if (containerColor.alpha > 0f) {
        containerColor
    } else {
        MiuixTheme.colorScheme.surface
    }
    val backdrop = rememberLayerBackdrop {
        drawRect(backdropBackground)
        drawContent()
    }
    
    CompositionLocalProvider(
        LocalMiuixBlurBackdrop provides backdrop,
        LocalMiuixBlurEnabled provides blurEnabled
    ) {
        Scaffold(
            modifier = modifier,
            topBar = topBar,
            bottomBar = bottomBar,
            floatingActionButton = floatingActionButton,
            floatingActionButtonPosition = floatingActionButtonPosition,
            containerColor = containerColor,
            contentWindowInsets = contentWindowInsets,
            popupHost = popupHost
        ) { paddingValues ->
            Box(modifier = if (blurEnabled) Modifier.layerBackdrop(backdrop) else Modifier) {
                content(paddingValues)
            }
        }
    }
}
