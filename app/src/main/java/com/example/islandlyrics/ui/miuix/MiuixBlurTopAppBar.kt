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
    color: Color = MiuixTheme.colorScheme.surface,
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
    val blurModifier = Modifier.miuixSurfaceBlur(
        enabled = blurEnabled,
        backdrop = backdrop,
        shape = RectangleShape,
        fallbackColor = color.copy(alpha = 0.72f)
    )

    TopAppBar(
        title = title,
        modifier = modifier.then(blurModifier),
        color = Color.Transparent,
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
    color: Color = MiuixTheme.colorScheme.surface,
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
    val blurModifier = Modifier.miuixSurfaceBlur(
        enabled = blurEnabled,
        backdrop = backdrop,
        shape = RectangleShape,
        fallbackColor = color.copy(alpha = 0.72f)
    )

    SmallTopAppBar(
        title = title,
        modifier = modifier.then(blurModifier),
        color = Color.Transparent,
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
