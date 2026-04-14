package com.example.islandlyrics.ui.miuix

import androidx.compose.foundation.layout.RowScope
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.basic.SmallTopAppBar
import top.yukonga.miuix.kmp.basic.TopAppBar
import top.yukonga.miuix.kmp.basic.TopAppBarDefaults
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
    titlePadding: androidx.compose.ui.unit.Dp = TopAppBarDefaults.TitlePadding,
    navigationIconPadding: androidx.compose.ui.unit.Dp = TopAppBarDefaults.NavigationIconPadding,
    actionIconPadding: androidx.compose.ui.unit.Dp = TopAppBarDefaults.ActionIconPadding,
    bottomContent: @Composable () -> Unit = {},
) {
    val backdrop = LocalMiuixBlurBackdrop.current
    val blurEnabled = LocalMiuixBlurEnabled.current
    val topBarColor = if (color == Color.Unspecified) neutralMiuixTopBarColor() else color
    val shouldUseBlur = blurEnabled && backdrop != null
    val blurModifier = Modifier.miuixSurfaceBlur(
        enabled = shouldUseBlur,
        backdrop = backdrop,
        shape = RectangleShape,
        fallbackColor = topBarColor,
        surfaceVariantColor = topBarColor
    )

    TopAppBar(
        title = title,
        modifier = modifier.then(blurModifier),
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
    titlePadding: androidx.compose.ui.unit.Dp = TopAppBarDefaults.TitlePadding,
    navigationIconPadding: androidx.compose.ui.unit.Dp = TopAppBarDefaults.NavigationIconPadding,
    actionIconPadding: androidx.compose.ui.unit.Dp = TopAppBarDefaults.ActionIconPadding,
    bottomContent: @Composable () -> Unit = {},
) {
    val backdrop = LocalMiuixBlurBackdrop.current
    val blurEnabled = LocalMiuixBlurEnabled.current
    val topBarColor = if (color == Color.Unspecified) neutralMiuixTopBarColor() else color
    val shouldUseBlur = blurEnabled && backdrop != null
    val blurModifier = Modifier.miuixSurfaceBlur(
        enabled = shouldUseBlur,
        backdrop = backdrop,
        shape = RectangleShape,
        fallbackColor = topBarColor,
        surfaceVariantColor = topBarColor
    )

    SmallTopAppBar(
        title = title,
        modifier = modifier.then(blurModifier),
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
