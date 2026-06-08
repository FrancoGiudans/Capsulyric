package com.example.islandlyrics.ui.miuix

import androidx.compose.runtime.Composable
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.textureBlur
import top.yukonga.miuix.kmp.shader.isRenderEffectSupported
import top.yukonga.miuix.kmp.theme.MiuixTheme

internal object MiuixBlurStyleDefaults {
    const val BlurRadius = 100f
    const val Contrast = 1f
    const val Saturation = 1f
    const val NoiseCoefficient = 0.0044f
    const val DialogBlurRadius = 72f
    const val DialogBrightness = 0f
    const val DialogContrast = 1.06f
    const val DialogSaturation = 1.1f
    const val DialogSurfaceAlpha = 0.6f
    const val DialogSurfaceVariantAlpha = 0.16f
    const val DialogNoiseCoefficient = 0.0045f
    const val DialogBorderAlpha = 0.06f
}

@Composable
internal fun miuixBlurColors(
    surfaceColor: Color,
    surfaceVariantColor: Color = MiuixTheme.colorScheme.surfaceVariant,
): BlurColors = BlurColors(
    blendColors = if (surfaceColor.luminance() > 0.5f) {
        listOf(
            BlendColorEntry(Color(0xE6BDBDBD), BlurBlendMode.Overlay),
            BlendColorEntry(Color(0x992B2B2B), BlurBlendMode.ColorDodge),
            BlendColorEntry(Color(0x339C9C9C), BlurBlendMode.SrcOver)
        )
    } else {
        listOf(
            BlendColorEntry(Color(0x667A7A7A), BlurBlendMode.ColorBurn),
            BlendColorEntry(Color(0x33747474), BlurBlendMode.Overlay),
            BlendColorEntry(Color(0x322B2B2B), BlurBlendMode.SrcOver)
        )
    },
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
    noiseCoefficient: Float = MiuixBlurStyleDefaults.NoiseCoefficient,
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
