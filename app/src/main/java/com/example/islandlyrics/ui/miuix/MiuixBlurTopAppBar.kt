package com.example.islandlyrics.ui.miuix

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
