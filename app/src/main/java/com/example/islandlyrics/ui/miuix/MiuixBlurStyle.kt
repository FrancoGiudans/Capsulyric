package com.example.islandlyrics.ui.miuix

import androidx.compose.runtime.Composable
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal object MiuixBlurStyleDefaults {
    const val BlurRadius = 52f
    const val Contrast = 1.04f
    const val Saturation = 1.08f
    const val SurfaceAlpha = 0.72f
    const val SurfaceVariantAlpha = 0.22f
}

@Composable
internal fun miuixBlurColors(
    surfaceColor: Color,
    surfaceVariantColor: Color = MiuixTheme.colorScheme.surfaceVariant,
): BlurColors = BlurColors(
    blendColors = listOf(
        BlendColorEntry(surfaceColor.copy(alpha = MiuixBlurStyleDefaults.SurfaceAlpha)),
        BlendColorEntry(surfaceVariantColor.copy(alpha = MiuixBlurStyleDefaults.SurfaceVariantAlpha))
    ),
    contrast = MiuixBlurStyleDefaults.Contrast,
    saturation = MiuixBlurStyleDefaults.Saturation
)

@Composable
internal fun Modifier.miuixSurfaceBlur(
    enabled: Boolean,
    backdrop: Backdrop?,
    shape: Shape,
    fallbackColor: Color,
    blurRadius: Float = MiuixBlurStyleDefaults.BlurRadius,
    surfaceVariantColor: Color = MiuixTheme.colorScheme.surfaceVariant,
): Modifier {
    return if (enabled && backdrop != null) {
        textureBlur(
            backdrop = backdrop,
            shape = shape,
            blurRadius = blurRadius,
            colors = miuixBlurColors(
                surfaceColor = fallbackColor,
                surfaceVariantColor = surfaceVariantColor
            )
        )
    } else {
        background(fallbackColor, shape)
    }
}
