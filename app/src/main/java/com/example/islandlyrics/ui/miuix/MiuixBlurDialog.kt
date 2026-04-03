package com.example.islandlyrics.ui.miuix

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Text
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.overlay.OverlayDialog
import top.yukonga.miuix.kmp.theme.MiuixTheme

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
    defaultWindowInsetsPadding: Boolean = true,
    renderInRootScaffold: Boolean = true,
    content: @Composable () -> Unit,
) {
    val backdrop = LocalMiuixBlurBackdrop.current
    val blurEnabled = LocalMiuixBlurEnabled.current
    val panelColor = MiuixTheme.colorScheme.surface.copy(alpha = 0.72f)

    OverlayDialog(
        show = show,
        title = null,
        summary = null,
        modifier = modifier,
        backgroundColor = Color.Transparent,
        enableWindowDim = enableWindowDim,
        onDismissRequest = onDismissRequest,
        onDismissFinished = onDismissFinished,
        outsideMargin = outsideMargin,
        insideMargin = DpSize.Zero,
        defaultWindowInsetsPadding = defaultWindowInsetsPadding,
        renderInRootScaffold = renderInRootScaffold,
    ) {
        val panelModifier = Modifier
            .fillMaxWidth()
            .then(
                if (blurEnabled && backdrop != null) {
                    Modifier.textureBlur(
                        backdrop = backdrop,
                        shape = BlurDialogShape,
                        blurRadius = 52f,
                        colors = BlurColors(
                            blendColors = listOf(
                                BlendColorEntry(panelColor),
                                BlendColorEntry(MiuixTheme.colorScheme.surfaceVariant.copy(alpha = 0.22f))
                            ),
                            contrast = 1.04f,
                            saturation = 1.08f
                        )
                    )
                } else {
                    Modifier.background(panelColor, BlurDialogShape)
                }
            )
            .padding(horizontal = 24.dp, vertical = 22.dp)

        Box(modifier = panelModifier) {
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
