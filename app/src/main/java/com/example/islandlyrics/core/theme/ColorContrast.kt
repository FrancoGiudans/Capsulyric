package com.example.islandlyrics.core.theme

import androidx.core.graphics.ColorUtils

internal fun ensureColorContrast(
    color: Int,
    background: Int,
    contrastColor: Int,
    minimumContrast: Double,
    minimumBlend: Float = 0f
): Int {
    val baseColor = ColorUtils.blendARGB(color, contrastColor, minimumBlend)
    if (ColorUtils.calculateContrast(baseColor, background) >= minimumContrast) return baseColor

    var low = minimumBlend
    var high = 1f
    repeat(20) {
        val blend = (low + high) / 2f
        val candidate = ColorUtils.blendARGB(color, contrastColor, blend)
        if (ColorUtils.calculateContrast(candidate, background) >= minimumContrast) {
            high = blend
        } else {
            low = blend
        }
    }
    return ColorUtils.blendARGB(color, contrastColor, high)
}
