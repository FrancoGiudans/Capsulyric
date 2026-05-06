package com.example.islandlyrics.ui.miuix

import androidx.compose.runtime.Composable
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.isRenderEffectSupported
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal object MiuixBlurStyleDefaults {
    const val BlurRadius = 52f
    const val Contrast = 1.04f
    const val Saturation = 1.08f
    const val SurfaceAlpha = 0.72f
    const val SurfaceVariantAlpha = 0.22f
    const val DialogBlurRadius = 52f
    const val DialogBrightness = 0f
    const val DialogContrast = 1.04f
    const val DialogSaturation = 1.08f
    const val DialogSurfaceAlpha = 0.72f
    const val DialogSurfaceVariantAlpha = 0.22f
    const val DialogNoiseCoefficient = 0.0045f
    const val DialogBorderAlpha = 0.06f
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
internal fun miuixDialogBlurColors(
    surfaceColor: Color,
    surfaceVariantColor: Color = surfaceColor,
): BlurColors {
    return BlurColors(
        blendColors = listOf(
            BlendColorEntry(
                surfaceColor.copy(alpha = MiuixBlurStyleDefaults.DialogSurfaceAlpha),
                BlurBlendMode.SrcOver
            ),
            BlendColorEntry(
                surfaceVariantColor.copy(alpha = MiuixBlurStyleDefaults.DialogSurfaceVariantAlpha),
                BlurBlendMode.SoftLight
            )
        ),
        brightness = MiuixBlurStyleDefaults.DialogBrightness,
        contrast = MiuixBlurStyleDefaults.DialogContrast,
        saturation = MiuixBlurStyleDefaults.DialogSaturation
    )
}

@Composable
internal fun Modifier.miuixSurfaceBlur(
    enabled: Boolean,
    backdrop: Backdrop?,
    shape: Shape,
    fallbackColor: Color,
    blurRadius: Float = MiuixBlurStyleDefaults.BlurRadius,
    noiseCoefficient: Float = BlurDefaults.NoiseCoefficient,
    surfaceVariantColor: Color = MiuixTheme.colorScheme.surfaceVariant,
    colors: BlurColors = miuixBlurColors(
        surfaceColor = fallbackColor,
        surfaceVariantColor = surfaceVariantColor
    ),
): Modifier {
    return if (enabled && backdrop != null && isRenderEffectSupported()) {
        textureBlur(
            backdrop = backdrop,
            shape = shape,
            blurRadius = blurRadius,
            noiseCoefficient = noiseCoefficient,
            colors = colors
        )
    } else {
        background(fallbackColor, shape)
    }
}
