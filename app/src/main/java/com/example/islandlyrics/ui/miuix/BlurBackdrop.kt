package com.example.islandlyrics.ui.miuix

import androidx.compose.runtime.compositionLocalOf
import top.yukonga.miuix.kmp.blur.Backdrop

/**
 * CompositionLocal to provide a [Backdrop] to child components for blur effects.
 */
val LocalMiuixBlurBackdrop = compositionLocalOf<Backdrop?> { null }

/**
 * CompositionLocal to provide a global blur enabled state.
 */
val LocalMiuixBlurEnabled = compositionLocalOf { false }
