/*
 * This file is part of Capsulyric (IslandLyrics).
 * Portions of this file are derived from or based on compose-miuix-ui/miuix
 * (https://github.com/compose-miuix-ui/miuix), version 0.9.3.
 *
 * Original: Copyright 2025, compose-miuix-ui contributors
 * Original license: Apache License 2.0 (full text: LICENSES/Apache-2.0.txt)
 *
 * Upstream source: TopAppBar / SmallTopAppBar (miuix-ui/src/commonMain/kotlin/top/yukonga/miuix/kmp/basic/TopAppBar.kt) and miuix-blur
 *
 * Modifications by FrancoGiudans for Capsulyric (IslandLyrics):
 *   - Added blurred background variants (MiuixBlurTopAppBar / MiuixBlurSmallTopAppBar).
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

import com.example.islandlyrics.ui.miuix.theme.neutralMiuixTopBarColor

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.unit.Dp
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.TopAppBarDefaults
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * A [TopAppBar] with blur effect.
 */
@Composable
fun MiuixBlurTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    titleColor: Color = MiuixTheme.colorScheme.onSurface,
    largeTitle: String = title,
    largeTitleColor: Color = MiuixTheme.colorScheme.onSurface,
    subtitle: String = "",
    subtitleColor: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: ScrollBehavior? = null,
    defaultWindowInsetsPadding: Boolean = true,
    titlePadding: Dp = TopAppBarDefaults.TitlePadding,
    navigationIconPadding: Dp = TopAppBarDefaults.NavigationIconPadding,
    actionIconPadding: Dp = TopAppBarDefaults.ActionIconPadding,
    bottomContent: @Composable () -> Unit = {},
) {
    val backdrop = LocalMiuixBlurBackdrop.current
    val blurEnabled = LocalMiuixBlurEnabled.current
    val topBarColor = if (color == Color.Unspecified) neutralMiuixTopBarColor() else color
    val shouldUseBlur = blurEnabled && backdrop != null

    DemoBlurredTopBar(
        modifier = modifier,
        backdrop = backdrop,
        blurEnabled = shouldUseBlur,
        surfaceColor = topBarColor
    ) {
        TopAppBar(
            title = title,
            color = if (shouldUseBlur) Color.Transparent else topBarColor,
            titleColor = titleColor,
            largeTitle = largeTitle,
            largeTitleColor = largeTitleColor,
            subtitle = subtitle,
            subtitleColor = subtitleColor,
            navigationIcon = navigationIcon,
            actions = actions,
            scrollBehavior = scrollBehavior,
            defaultWindowInsetsPadding = defaultWindowInsetsPadding,
            titlePadding = titlePadding,
            navigationIconPadding = navigationIconPadding,
            actionIconPadding = actionIconPadding,
            bottomContent = bottomContent
        )
    }
}

/**
 * A [SmallTopAppBar] with blur effect.
 */
@Composable
fun MiuixBlurSmallTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    color: Color = Color.Unspecified,
    titleColor: Color = MiuixTheme.colorScheme.onSurface,
    subtitle: String = "",
    subtitleColor: Color = MiuixTheme.colorScheme.onSurfaceVariantSummary,
    navigationIcon: @Composable () -> Unit = {},
    actions: @Composable RowScope.() -> Unit = {},
    scrollBehavior: ScrollBehavior? = null,
    defaultWindowInsetsPadding: Boolean = true,
    titlePadding: Dp = TopAppBarDefaults.TitlePadding,
    navigationIconPadding: Dp = TopAppBarDefaults.NavigationIconPadding,
    actionIconPadding: Dp = TopAppBarDefaults.ActionIconPadding,
    bottomContent: @Composable () -> Unit = {},
) {
    val backdrop = LocalMiuixBlurBackdrop.current
    val blurEnabled = LocalMiuixBlurEnabled.current
    val topBarColor = if (color == Color.Unspecified) neutralMiuixTopBarColor() else color
    val shouldUseBlur = blurEnabled && backdrop != null

    DemoBlurredTopBar(
        modifier = modifier,
        backdrop = backdrop,
        blurEnabled = shouldUseBlur,
        surfaceColor = topBarColor
    ) {
        SmallTopAppBar(
            title = title,
            color = if (shouldUseBlur) Color.Transparent else topBarColor,
            titleColor = titleColor,
            subtitle = subtitle,
            subtitleColor = subtitleColor,
            navigationIcon = navigationIcon,
            actions = actions,
            scrollBehavior = scrollBehavior,
            defaultWindowInsetsPadding = defaultWindowInsetsPadding,
            titlePadding = titlePadding,
            navigationIconPadding = navigationIconPadding,
            actionIconPadding = actionIconPadding,
            bottomContent = bottomContent
        )
    }
}

@Composable
private fun DemoBlurredTopBar(
    modifier: Modifier = Modifier,
    backdrop: top.yukonga.miuix.kmp.blur.Backdrop?,
    blurEnabled: Boolean,
    surfaceColor: Color,
    content: @Composable () -> Unit,
) {
    val blurModifier = if (blurEnabled && backdrop != null) {
        Modifier.textureBlur(
            backdrop = backdrop,
            shape = RectangleShape,
            blurRadius = 25f,
            colors = BlurColors(
                blendColors = listOf(
                    BlendColorEntry(color = surfaceColor.copy(alpha = 0.8f))
                )
            )
        )
    } else {
        Modifier
    }

    Box(modifier = modifier.then(blurModifier)) {
        content()
    }
}


