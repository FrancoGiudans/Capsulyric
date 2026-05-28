package com.example.islandlyrics.ui.common

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.pager.PagerState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import kotlin.math.abs

@Composable
fun LayeredPagerPage(
    pagerState: PagerState,
    page: Int,
    backdropColor: Color,
    modifier: Modifier = Modifier,
    scrimColor: Color = Color.Black,
    content: @Composable BoxScope.() -> Unit,
) {
    val rawOffset = (pagerState.currentPage - page) + pagerState.currentPageOffsetFraction
    val clampedOffset = rawOffset.coerceIn(-1f, 1f)
    val direction = pagerState.currentPageOffsetFraction
    val absOffset = abs(clampedOffset)
    val transitionActive = abs(direction) > 0.0001f

    val isIncoming = when {
        !transitionActive -> clampedOffset == 0f
        direction > 0f -> clampedOffset < 0f
        else -> clampedOffset > 0f
    }

    val incomingProgress = if (isIncoming) 1f - absOffset else 0f
    val outgoingProgress = if (!isIncoming && absOffset < 1f) absOffset else 0f
    val pageZIndex = when {
        isIncoming && transitionActive -> 2f
        absOffset < 0.001f -> 1f
        else -> 0f
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .zIndex(pageZIndex)
            .clipToBounds()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val width = size.width.toFloat()
                    if (isIncoming) {
                        translationX = -clampedOffset * width
                        val scale = 1.01f - (0.01f * incomingProgress)
                        scaleX = scale
                        scaleY = scale
                        alpha = 0.94f + (0.06f * incomingProgress)
                    } else {
                        val directionSign = when {
                            direction > 0f -> -1f
                            direction < 0f -> 1f
                            else -> 0f
                        }
                        translationX = width * 0.08f * outgoingProgress * directionSign
                        val scale = 1f - (0.045f * outgoingProgress)
                        scaleX = scale
                        scaleY = scale
                        alpha = 1f - (0.04f * outgoingProgress)
                    }
                }
                .background(backdropColor)
        ) {
            content()

            val scrimAlpha = if (isIncoming) 0f else 0.08f * outgoingProgress
            if (scrimAlpha > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(scrimColor.copy(alpha = scrimAlpha))
                )
            }
        }
    }
}
