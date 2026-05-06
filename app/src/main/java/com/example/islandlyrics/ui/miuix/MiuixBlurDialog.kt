package com.example.islandlyrics.ui.miuix

import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.basic.Text
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
    val shouldUseBlur = blurEnabled && backdrop != null
    val panelColor = MiuixTheme.colorScheme.surface
    val borderColor = if (panelColor.luminance() > 0.5f) {
        Color.White
    } else {
        MiuixTheme.colorScheme.onSurface
    }.copy(alpha = MiuixBlurStyleDefaults.DialogBorderAlpha)

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
            .clip(BlurDialogShape)
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
