package com.example.islandlyrics.ui.common

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.zIndex
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.launch

@Composable
fun <T> PageStackHost(
    stack: List<T>,
    onPop: () -> Unit,
    backdropColor: Color,
    modifier: Modifier = Modifier,
    key: (T) -> Any = { it as Any },
    backgroundContent: @Composable BoxScope.() -> Unit,
    pageContent: @Composable BoxScope.(T) -> Unit,
) {
    val displayedStack = remember { mutableStateListOf<T>() }
    val transitionProgress = remember { Animatable(1f) }
    val targetKeys = stack.map(key)
    val currentOnPop by rememberUpdatedState(onPop)
    val scope = rememberCoroutineScope()
    val navEventState = rememberNavigationEventState(NavigationEventInfo.None)
    var latestGestureProgress by remember { mutableFloatStateOf(0f) }
    val transitionState = navEventState.transitionState
    val gestureState = transitionState as? NavigationEventTransitionState.InProgress
    val gestureProgress = gestureState?.latestEvent?.progress ?: 0f

    LaunchedEffect(targetKeys) {
        when {
            stack.size > displayedStack.size -> {
                displayedStack.addAll(stack.drop(displayedStack.size))
                transitionProgress.snapTo(0f)
                transitionProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = pageStackTween(durationMillis = 430)
                )
            }
            stack.size < displayedStack.size -> {
                transitionProgress.stop()
                transitionProgress.animateTo(
                    targetValue = 0f,
                    animationSpec = pageStackTween(durationMillis = 380)
                )
                while (displayedStack.size > stack.size) {
                    displayedStack.removeAt(displayedStack.lastIndex)
                }
                transitionProgress.snapTo(1f)
            }
            targetKeys != displayedStack.map(key) -> {
                displayedStack.clear()
                displayedStack.addAll(stack)
                transitionProgress.snapTo(1f)
            }
        }
    }
    LaunchedEffect(gestureProgress) {
        latestGestureProgress = gestureProgress
    }

    NavigationBackHandler(
        state = navEventState,
        isBackEnabled = displayedStack.isNotEmpty(),
        onBackCompleted = {
            scope.launch {
                transitionProgress.snapTo((1f - latestGestureProgress).coerceIn(0f, 1f))
                currentOnPop()
            }
        }
    )

    val topCoverProgress = if (gestureState != null) {
        1f - gestureProgress
    } else {
        transitionProgress.value
    }.coerceIn(0f, 1f)
    val depth = displayedStack.size

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(backdropColor)
    ) {
        PageStackLayer(
            zIndex = 0f,
            coverProgress = if (depth == 1) topCoverProgress else if (depth > 1) 1f else 0f,
            isUnderlay = depth > 0,
            backdropColor = backdropColor
        ) {
            backgroundContent()
        }

        displayedStack.forEachIndexed { index, page ->
            val isTop = index == depth - 1
            val isImmediateUnderlay = index == depth - 2
            val pageProgress = when {
                isTop -> topCoverProgress
                isImmediateUnderlay -> topCoverProgress
                index < depth - 2 -> 1f
                else -> 1f
            }

            PageStackLayer(
                zIndex = (index + 1).toFloat(),
                coverProgress = pageProgress,
                isTop = isTop,
                isUnderlay = isImmediateUnderlay,
                isHiddenBelowUnderlay = index < depth - 2,
                backdropColor = backdropColor
            ) {
                pageContent(page)
            }
        }
    }
}

@Composable
private fun PageStackLayer(
    zIndex: Float,
    coverProgress: Float,
    isTop: Boolean = false,
    isUnderlay: Boolean = false,
    isHiddenBelowUnderlay: Boolean = false,
    backdropColor: Color,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .zIndex(zIndex)
            .graphicsLayer {
                val width = size.width
                when {
                    isTop -> {
                        translationX = width * (1f - coverProgress)
                        alpha = 0.96f + 0.04f * coverProgress
                    }
                    isUnderlay -> {
                        translationX = -width * 0.105f * coverProgress
                        val scale = 1f - 0.035f * coverProgress
                        scaleX = scale
                        scaleY = scale
                        alpha = 1f - 0.04f * coverProgress
                    }
                    isHiddenBelowUnderlay -> {
                        alpha = 0f
                    }
                }
            }
            .background(backdropColor)
    ) {
        content()
        if (isUnderlay && coverProgress > 0f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.06f * coverProgress))
            )
        }
    }
}

private fun pageStackTween(durationMillis: Int) = tween<Float>(
    durationMillis = durationMillis,
    easing = FastOutSlowInEasing
)
