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

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Window layout tier derived from actual container dimensions (width, height, aspect)
 * so phone landscape, foldables, tablets, split screens, and desktop windows each get
 * appropriate chrome and content density.
 */
enum class WindowSizeTier {
    CompactPortrait,
    CompactLandscape,
    Medium,
    Expanded,
    ExtraLarge,
}

@Immutable
data class AppWindowLayoutInfo(
    val widthDp: Dp,
    val heightDp: Dp,
    val tier: WindowSizeTier,
) {
    val isLandscape: Boolean get() = widthDp > heightDp
    val isShortHeight: Boolean get() = heightDp < 480.dp
    val useNavigationRail: Boolean get() = tier != WindowSizeTier.CompactPortrait
    val expandNavigationRailByDefault: Boolean get() = tier == WindowSizeTier.ExtraLarge
    val showNavigationRailToggle: Boolean
        get() = tier == WindowSizeTier.Medium ||
            tier == WindowSizeTier.Expanded ||
            tier == WindowSizeTier.ExtraLarge
    val useCompactTopBar: Boolean get() = tier == WindowSizeTier.CompactLandscape
    val useTwoPanePreview: Boolean get() = widthDp >= 600.dp && (isLandscape || widthDp >= 720.dp)
    val useMasterDetail: Boolean get() = tier == WindowSizeTier.Expanded || tier == WindowSizeTier.ExtraLarge
    val maxReadableWidth: Dp
        get() = when (tier) {
            WindowSizeTier.CompactPortrait -> Dp.Unspecified
            WindowSizeTier.CompactLandscape -> 720.dp
            WindowSizeTier.Medium -> 680.dp
            WindowSizeTier.Expanded -> 720.dp
            WindowSizeTier.ExtraLarge -> 760.dp
        }
}

fun classifyAppWindowLayout(widthDp: Dp, heightDp: Dp): AppWindowLayoutInfo {
    val safeWidth = widthDp.coerceAtLeast(0.dp)
    val safeHeight = heightDp.coerceAtLeast(0.dp)
    val tier = when {
        safeHeight < 480.dp && safeWidth > safeHeight -> WindowSizeTier.CompactLandscape
        safeWidth >= 1200.dp -> WindowSizeTier.ExtraLarge
        safeWidth >= 840.dp -> WindowSizeTier.Expanded
        safeWidth >= 600.dp -> WindowSizeTier.Medium
        else -> WindowSizeTier.CompactPortrait
    }
    return AppWindowLayoutInfo(
        widthDp = safeWidth,
        heightDp = safeHeight,
        tier = tier,
    )
}

@Composable
fun rememberAppWindowLayoutInfo(): AppWindowLayoutInfo {
    val containerDpSize = LocalWindowInfo.current.containerDpSize
    val configuration = LocalConfiguration.current
    val widthDp = if (containerDpSize.width > 0.dp) {
        containerDpSize.width
    } else {
        configuration.screenWidthDp.dp
    }
    val heightDp = if (containerDpSize.height > 0.dp) {
        containerDpSize.height
    } else {
        configuration.screenHeightDp.dp
    }
    return remember(widthDp, heightDp) {
        classifyAppWindowLayout(widthDp = widthDp, heightDp = heightDp)
    }
}

val LocalAppWindowLayout = compositionLocalOf {
    AppWindowLayoutInfo(
        widthDp = 412.dp,
        heightDp = 915.dp,
        tier = WindowSizeTier.CompactPortrait,
    )
}

val LocalAdaptiveHasStartRail = compositionLocalOf { false }

@Composable
fun ProvideAppWindowLayout(content: @Composable () -> Unit) {
    val layoutInfo = rememberAppWindowLayoutInfo()
    CompositionLocalProvider(LocalAppWindowLayout provides layoutInfo) {
        content()
    }
}
