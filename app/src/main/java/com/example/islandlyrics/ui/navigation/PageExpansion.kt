/*
 * Copyright (c) 2026 FrancoGiudans
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.islandlyrics.ui.navigation

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp

class PageExpansion private constructor(
    internal val origin: Rect,
) {
    companion object {
        fun fromWindowBounds(anchor: LayoutCoordinates?, host: Rect): PageExpansion? {
            if (anchor == null || !anchor.isAttached) return null
            val visible = anchor.boundsInWindow().intersect(host)
            if (visible.isEmpty) return null
            val local = visible.translate(-host.topLeft)
            return PageExpansion(
                Rect(
                    local.left / host.width,
                    local.top / host.height,
                    local.right / host.width,
                    local.bottom / host.height,
                )
            )
        }
    }
}

internal val LocalPageExpansionProgress = staticCompositionLocalOf<() -> Float> { { 1f } }

internal fun Modifier.expandFrom(
    expansion: PageExpansion,
    progress: () -> Float,
): Modifier = graphicsLayer {
    val fraction = progress().coerceIn(0f, 1f)
    alpha = (fraction * 3f).coerceAtMost(1f)
    if (fraction < 1f) {
        // Clip the composited layer, including any nested backdrop render effects.
        shape = ExpansionShape(expansion.origin, fraction)
        clip = true
    }
}

private data class ExpansionShape(val origin: Rect, val fraction: Float) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Rounded(
            RoundRect(
                rect = Rect(
                    lerp(origin.left * size.width, 0f, fraction),
                    lerp(origin.top * size.height, 0f, fraction),
                    lerp(origin.right * size.width, size.width, fraction),
                    lerp(origin.bottom * size.height, size.height, fraction),
                ),
                cornerRadius = CornerRadius(with(density) { 24.dp.toPx() } * (1f - fraction)),
            )
        )
}
