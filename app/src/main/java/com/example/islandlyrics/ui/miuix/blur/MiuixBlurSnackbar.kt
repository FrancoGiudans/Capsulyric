/*
 * This file is part of Capsulyric (IslandLyrics).
 * Portions of this file are derived from or based on compose-miuix-ui/miuix
 * (https://github.com/compose-miuix-ui/miuix), version 0.9.3.
 *
 * Original: Copyright 2025, compose-miuix-ui contributors
 * Original license: Apache License 2.0 (full text: LICENSES/Apache-2.0.txt)
 *
 * Upstream source: Snackbar (miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/basic/Snackbar.kt)
 *
 * Modifications by FrancoGiudans for Capsulyric (IslandLyrics):
 *   - Replaced the solid squircle background with a MIUIX texture-blur surface
 *     when a blur backdrop is available, keeping the squircle fallback.
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

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.isTraversalGroup
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import top.yukonga.miuix.kmp.basic.ButtonDefaults
import top.yukonga.miuix.kmp.basic.Icon
import top.yukonga.miuix.kmp.basic.SnackbarColors
import top.yukonga.miuix.kmp.basic.SnackbarData
import top.yukonga.miuix.kmp.basic.SnackbarDefaults
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.basic.TextButton
import top.yukonga.miuix.kmp.icon.MiuixIcons
import top.yukonga.miuix.kmp.icon.basic.Close
import top.yukonga.miuix.kmp.squircle.squircleBackground
import top.yukonga.miuix.kmp.theme.LocalContentColor
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * A [Snackbar] variant with a MIUIX texture-blur background.
 *
 * When [LocalMiuixBlurBackdrop] is provided by the surrounding scaffold and
 * blur is enabled, the snackbar surface is drawn with the frosted MIUIX blur;
 * otherwise it falls back to the upstream squircle background.
 */
@Composable
fun MiuixBlurSnackbar(
    data: SnackbarData,
    modifier: Modifier = Modifier,
    cornerRadius: Dp = SnackbarDefaults.CornerRadius,
    colors: SnackbarColors = SnackbarDefaults.snackbarColors(),
    insideMargin: PaddingValues = SnackbarDefaults.InsideMargin,
) {
    val visuals = data.visuals
    val scope = rememberCoroutineScope()
    val backdrop = LocalMiuixBlurBackdrop.current
    val blurEnabled = LocalMiuixBlurEnabled.current
    val shouldUseBlur = blurEnabled && backdrop != null && !LocalMiuixBlurSurfaceActive.current
    val surfaceShape = RoundedCornerShape(cornerRadius)

    CompositionLocalProvider(
        LocalContentColor provides colors.contentColor,
    ) {
        Box(
            modifier = modifier
                .semantics(mergeDescendants = false) {
                    isTraversalGroup = true
                    liveRegion = LiveRegionMode.Polite
                }
                .padding(SnackbarDefaults.OuterPadding)
                .dropShadow(
                    shape = surfaceShape,
                    shadow = Shadow(
                        radius = 10.dp,
                        color = Color.Black,
                        alpha = 0.1f,
                    ),
                )
                .then(
                    if (shouldUseBlur) {
                        Modifier.miuixSurfaceBlur(
                            enabled = true,
                            backdrop = backdrop,
                            shape = surfaceShape,
                            fallbackColor = colors.containerColor,
                            blurRadius = MiuixBlurStyleDefaults.SnackbarBlurRadius,
                            noiseCoefficient = MiuixBlurStyleDefaults.SnackbarNoiseCoefficient,
                            colors = miuixDialogBlurColors(surfaceColor = colors.containerColor),
                        )
                    } else {
                        Modifier.squircleBackground(
                            color = colors.containerColor,
                            cornerRadius = cornerRadius,
                        )
                    }
                )
                .pointerInput(Unit) {
                    detectTapGestures { /* Consume click */ }
                },
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .defaultMinSize(minHeight = 48.dp)
                    .padding(insideMargin),
            ) {
                Text(
                    text = visuals.message,
                    color = colors.contentColor,
                    style = MiuixTheme.textStyles.body2,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                val actionLabel = visuals.actionLabel
                if (!actionLabel.isNullOrEmpty()) {
                    val onAction by rememberUpdatedState(data::performAction)
                    TextButton(
                        text = actionLabel,
                        onClick = { scope.launch { onAction() } },
                        modifier = Modifier.padding(start = 12.dp),
                        cornerRadius = SnackbarDefaults.ActionCornerRadius,
                        minWidth = 26.dp,
                        minHeight = 26.dp,
                        colors = ButtonDefaults.textButtonColorsPrimary(
                            color = colors.actionContainerColor,
                            textColor = colors.actionContentColor,
                        ),
                        insideMargin = SnackbarDefaults.ActionInsideMargin,
                        textStyle = TextStyle(fontSize = 15.sp),
                    )
                }

                if (visuals.withDismissAction) {
                    val onDismiss by rememberUpdatedState(data::dismiss)
                    Icon(
                        imageVector = MiuixIcons.Basic.Close,
                        contentDescription = "Dismiss",
                        tint = colors.dismissActionContentColor,
                        modifier = Modifier
                            .padding(start = 8.dp)
                            .size(20.dp)
                            .clickable(
                                indication = null,
                                interactionSource = null,
                            ) {
                                scope.launch { onDismiss() }
                            },
                    )
                }
            }
        }
    }
}
