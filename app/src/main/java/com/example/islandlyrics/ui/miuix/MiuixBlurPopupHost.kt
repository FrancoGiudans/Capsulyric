package com.example.islandlyrics.ui.miuix

import androidx.compose.runtime.Composable
import top.yukonga.miuix.kmp.utils.MiuixPopupUtils.Companion.MiuixPopupHost

/**
 * Blur popup host compatibility wrapper.
 *
 * The pinned miuix version already provides its own popup host, while the
 * internal popup state APIs needed for a custom blur implementation are not public.
 */
@Composable
fun MiuixBlurPopupHost() {
    MiuixPopupHost()
}
