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

package com.example.islandlyrics.ui.adaptive

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp

/**
 * Merges Scaffold [PaddingValues] while preserving horizontal cutout/navigation-bar insets.
 */
fun PaddingValues.adaptiveContentPadding(
    layoutDirection: LayoutDirection,
    extraTop: Dp = 0.dp,
    extraBottom: Dp = 0.dp,
    extraStart: Dp = 0.dp,
    extraEnd: Dp = 0.dp,
): PaddingValues = PaddingValues(
    start = calculateStartPadding(layoutDirection) + extraStart,
    top = calculateTopPadding() + extraTop,
    end = calculateEndPadding(layoutDirection) + extraEnd,
    bottom = calculateBottomPadding() + extraBottom,
)

/**
 * Centers content horizontally and caps width on wider windows while remaining unconstrained
 * on compact portrait screens.
 */
@Composable
fun AdaptiveReadableBox(
    modifier: Modifier = Modifier,
    maxWidth: Dp = LocalAppWindowLayout.current.maxReadableWidth,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = modifier.fillMaxWidth(),
        contentAlignment = Alignment.TopCenter,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (maxWidth != Dp.Unspecified) Modifier.widthIn(max = maxWidth) else Modifier),
            content = content,
        )
    }
}

/**
 * Wraps a LazyColumn item so edge overscroll/scrollbars remain full-width while cards stay
 * readable on medium and larger screens.
 */
@Composable
fun AdaptiveWidthItem(
    modifier: Modifier = Modifier,
    maxWidth: Dp = LocalAppWindowLayout.current.maxReadableWidth,
    content: @Composable BoxScope.() -> Unit,
) {
    AdaptiveReadableBox(
        modifier = modifier,
        maxWidth = maxWidth,
        content = content,
    )
}

/**
 * Splits available horizontal space into master/preview and detail/settings panes.
 */
@Composable
fun AdaptiveTwoPane(
    startWeight: Float = 0.40f,
    spacing: Dp = 0.dp,
    dividerColor: Color? = null,
    modifier: Modifier = Modifier,
    startPane: @Composable BoxScope.() -> Unit,
    endPane: @Composable BoxScope.() -> Unit,
) {
    val clampedStartWeight = startWeight.coerceIn(0.25f, 0.75f)
    Row(modifier = modifier.fillMaxWidth()) {
        Box(
            modifier = Modifier
                .weight(clampedStartWeight)
                .fillMaxHeight(),
            content = startPane,
        )
        if (dividerColor != null) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(1.dp)
                    .background(dividerColor),
            )
        }
        if (spacing > 0.dp) {
            Spacer(modifier = Modifier.width(spacing))
        }
        Box(
            modifier = Modifier
                .weight(1f - clampedStartWeight)
                .fillMaxHeight(),
            content = endPane,
        )
    }
}
