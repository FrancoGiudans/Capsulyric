/*
 * This file is part of Capsulyric (IslandLyrics).
 * Portions of this file are derived from or based on compose-miuix-ui/miuix
 * (https://github.com/compose-miuix-ui/miuix), version 0.9.3.
 *
 * Original: Copyright 2025, compose-miuix-ui contributors
 * Original license: Apache License 2.0 (full text: LICENSES/Apache-2.0.txt)
 *
 * Upstream source: OverlayDialog (miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/overlay/OverlayDialog.kt) and miuix-blur
 *
 * Modifications by FrancoGiudans for Capsulyric (IslandLyrics):
 *   - Added blur backdrop (LocalMiuixBlurBackdrop), edge highlight, 28.dp corner radius and adjusted defaults while keeping the OverlayDialog API surface.
 */

/*
 *
 *  * Copyright (c) 2026 FrancoGiudans
 *  *
 *  * This file is part of Capsulyric.
 *  *
 *  * Capsulyric is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * Capsulyric is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with Capsulyric. If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package com.example.islandlyrics.ui.miuix.blur

import android.os.Build
import android.view.View
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.captionBar
import androidx.compose.foundation.layout.captionBarPadding
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.OnBackInvokedDefaultInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.theme.MiuixTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties

private val BlurDialogShape = RoundedCornerShape(28.dp)

@Composable
fun MiuixBlurDialog(
    show: Boolean,
    title: String? = null,
    summary: String? = null,
    modifier: Modifier = Modifier,
    enableWindowDim: Boolean = true,
    onDismissRequest: (() -> Unit)? = null,
    onDismissFinished: (() -> Unit)? = null,
    outsideMargin: DpSize = DpSize(24.dp, 24.dp),
    insideMargin: DpSize = DpSize.Zero,
    defaultWindowInsetsPadding: Boolean = true,
    @Suppress("UNUSED_PARAMETER")
    renderInRootScaffold: Boolean = true,
    content: @Composable () -> Unit,
) {
    if (!show) {
        return
    }

    val backdrop = LocalMiuixBlurBackdrop.current
    val blurEnabled = LocalMiuixBlurEnabled.current
    val shouldUseBlur = blurEnabled && backdrop != null
    val colorScheme = MiuixTheme.colorScheme
    val panelColor = MiuixTheme.colorScheme.surface
    val isLightPanel = panelColor.luminance() > 0.5f
    val dialogContentColors = if (isLightPanel) {
        val secondaryVariant = lerp(panelColor, Color.Black, 0.08f)
        colorScheme.copy(
            secondaryVariant = secondaryVariant,
            disabledSecondaryVariant = secondaryVariant.copy(alpha = 0.72f)
        )
    } else {
        colorScheme
    }
    val currentOnDismissRequest by rememberUpdatedState(onDismissRequest)
    val windowInfo = LocalWindowInfo.current
    val isLargeScreen = remember(windowInfo.containerDpSize) {
        windowInfo.containerDpSize.height >= 480.dp && windowInfo.containerDpSize.width >= 840.dp
    }
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val captionBarPadding = WindowInsets.captionBar.asPaddingValues().calculateTopPadding()
    val displayCutoutPadding = WindowInsets.displayCutout.asPaddingValues().calculateTopPadding()
    val topInset = remember(statusBarPadding, captionBarPadding, displayCutoutPadding) {
        maxOf(statusBarPadding, captionBarPadding, displayCutoutPadding)
    }
    val borderColor = if (isLightPanel) {
        Color.White
    } else {
        MiuixTheme.colorScheme.onSurface
    }.copy(alpha = MiuixBlurStyleDefaults.DialogBorderAlpha)

    Dialog(
        onDismissRequest = { currentOnDismissRequest?.invoke() },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        val dialogView = LocalView.current
        val dialogDispatcher = remember { NavigationEventDispatcher() }
        val dialogDispatcherOwner = remember(dialogDispatcher) {
            object : androidx.navigationevent.NavigationEventDispatcherOwner {
                override val navigationEventDispatcher = dialogDispatcher
            }
        }
        DisposableEffect(dialogView) {
            if (Build.VERSION.SDK_INT >= 33) {
                val backDispatcher = dialogView.findOnBackInvokedDispatcher()
                if (backDispatcher != null) {
                    val input = OnBackInvokedDefaultInput(backDispatcher)
                    dialogDispatcher.addInput(input)
                    onDispose { dialogDispatcher.removeInput(input) }
                } else {
                    onDispose { }
                }
            } else {
                onDispose { }
            }
        }

        val navEventState = rememberNavigationEventState(NavigationEventInfo.None)
        val currentDismiss by rememberUpdatedState(currentOnDismissRequest)

        CompositionLocalProvider(
            LocalNavigationEventDispatcherOwner provides dialogDispatcherOwner
        ) {
            NavigationBackHandler(
                state = navEventState,
                isBackEnabled = true,
                onBackCompleted = { currentDismiss?.invoke() }
            )
        }

        val transitionState = navEventState.transitionState
        val progressInProgress = transitionState as? NavigationEventTransitionState.InProgress
        val gestureProgress = progressInProgress?.latestEvent?.progress ?: 0f
        val isGestureActive = progressInProgress != null
        val translationYPx = with(LocalDensity.current) { 200.dp.toPx() } * gestureProgress
        Box(modifier = Modifier.fillMaxSize()) {
            if (enableWindowDim) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(MiuixTheme.colorScheme.windowDimming)
                )
            }

            Box(
                modifier = Modifier
                    .then(
                        if (defaultWindowInsetsPadding) {
                            Modifier
                                .imePadding()
                                .navigationBarsPadding()
                                .captionBarPadding()
                        } else {
                            Modifier
                        }
                    )
                    .fillMaxSize()
                    .pointerInput(currentOnDismissRequest) {
                        detectTapGestures(
                            onTap = { currentOnDismissRequest?.invoke() }
                        )
                    }
                    .padding(horizontal = outsideMargin.width)
                    .padding(top = topInset, bottom = outsideMargin.height)
            ) {
                val panelModifier = Modifier
                    .align(if (isLargeScreen) Alignment.Center else Alignment.BottomCenter)
                    .widthIn(max = 420.dp)
                    .fillMaxWidth()
                    .then(modifier)
                    .graphicsLayer {
                        if (isGestureActive) {
                            this.translationY = translationYPx
                            this.alpha = 1f - gestureProgress * 0.3f
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { }
                    }
                    .graphicsLayer {
                        shape = BlurDialogShape
                        clip = true
                        compositingStrategy = CompositingStrategy.Offscreen
                    }
                    .miuixSurfaceBlur(
                        enabled = shouldUseBlur,
                        backdrop = backdrop,
                        shape = BlurDialogShape,
                        fallbackColor = panelColor,
                        blurRadius = MiuixBlurStyleDefaults.DialogBlurRadius,
                        noiseCoefficient = MiuixBlurStyleDefaults.DialogNoiseCoefficient,
                        colors = miuixDialogBlurColors(surfaceColor = panelColor)
                    )
                    .border(1.dp, borderColor, BlurDialogShape)
                    .padding(
                        horizontal = 24.dp + insideMargin.width,
                        vertical = 22.dp + insideMargin.height
                    )

                CompositionLocalProvider(LocalMiuixBlurSurfaceActive provides true) {
                    Box(modifier = panelModifier) {
                        MiuixTheme(colors = dialogContentColors) {
                            Column {
                                if (!title.isNullOrBlank()) {
                                    Text(
                                        text = title,
                                        color = MiuixTheme.colorScheme.onSurface,
                                        fontSize = MiuixTheme.textStyles.title3.fontSize
                                    )
                                }
                                if (!summary.isNullOrBlank()) {
                                    if (!title.isNullOrBlank()) {
                                        Spacer(modifier = Modifier.height(8.dp))
                                    }
                                    Text(
                                        text = summary,
                                        color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
                                        fontSize = MiuixTheme.textStyles.body2.fontSize
                                    )
                                }
                                if (!title.isNullOrBlank() || !summary.isNullOrBlank()) {
                                    Spacer(modifier = Modifier.height(20.dp))
                                }
                                content()
                            }
                        }
                    }
                }
            }
            MiuixBlurPopupHost()
        }
    }

}
