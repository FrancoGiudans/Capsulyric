/*
 * This file is part of Capsulyric (IslandLyrics).
 * Portions of this file are derived from or based on compose-miuix-ui/miuix
 * (https://github.com/compose-miuix-ui/miuix), version 0.9.3.
 *
 * Original: Copyright 2025, compose-miuix-ui contributors
 * Original license: Apache License 2.0 (full text: LICENSES/Apache-2.0.txt)
 *
 * Upstream source: miuix-blur style APIs: BlurDefaults, BlurColors, BlendColorEntry, textureBlur, Highlight, BloomStroke, LightSource
 *
 * Modifications by FrancoGiudans for Capsulyric (IslandLyrics):
 *   - Custom blur color/highlight presets built on miuix-blur defaults.
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

import androidx.compose.runtime.Composable
import androidx.compose.foundation.background
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import top.yukonga.miuix.kmp.blur.Backdrop
import top.yukonga.miuix.kmp.blur.BlendColorEntry
import top.yukonga.miuix.kmp.blur.BlurBlendMode
import top.yukonga.miuix.kmp.blur.BlurColors
import top.yukonga.miuix.kmp.blur.BlurDefaults
import top.yukonga.miuix.kmp.blur.highlight.BloomStroke
import top.yukonga.miuix.kmp.blur.highlight.Highlight
import top.yukonga.miuix.kmp.blur.highlight.LightPosition
import top.yukonga.miuix.kmp.blur.highlight.LightSource
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

    const val BottomSheetBlurRadius = 72f
    const val BottomSheetNoiseCoefficient = 0.0045f
    const val BottomSheetBorderAlpha = 0.06f
}

private fun uniformEdgeHighlight(width: Dp, alpha: Float): Highlight = Highlight(
    width = width,
    style = BloomStroke(
        color = Color.White.copy(alpha = alpha),
        innerBlurRadius = 0.dp,
        primaryLight = LightSource(
            position = LightPosition(0.5f, 0.5f, -0.5f),
            intensity = 0f,
        ),
        secondaryLight = LightSource(
            position = LightPosition(0.5f, 0.95f, -0.5f),
            intensity = 0f,
        ),
    ),
)

private val MiuixLightUniformEdgeHighlight = uniformEdgeHighlight(
    width = 1.25.dp,
    alpha = 0.45f,
)

private val MiuixDarkUniformEdgeHighlight = uniformEdgeHighlight(
    width = 1.dp,
    alpha = 0.18f,
)

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
internal fun miuixBlurHighlight(surfaceColor: Color): Highlight? {
    if (!LocalMiuixBlurEdgeHighlightEnabled.current) {
        return null
    }
    return if (surfaceColor.luminance() > 0.5f) {
        MiuixLightUniformEdgeHighlight
    } else {
        MiuixDarkUniformEdgeHighlight
    }
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
            colors = colors,
            highlight = miuixBlurHighlight(fallbackColor)
        )
    } else {
        background(fallbackColor, shape)
    }
}

