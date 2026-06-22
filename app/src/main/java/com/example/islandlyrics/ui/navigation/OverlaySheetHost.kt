package com.example.islandlyrics.ui.navigation

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEvent.Companion.EDGE_LEFT
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import com.example.islandlyrics.core.settings.AppPreferences

@Composable
fun OverlaySheetHost(
    visible: Boolean,
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    scrimColor: Color = Color.Black,
    content: @Composable BoxScope.() -> Unit,
    sheetContent: @Composable BoxScope.() -> Unit,
) {
    val context = LocalContext.current
    val prefs = remember {
        AppPreferences.of(context)
    }
    val currentOnDismissRequest by rememberUpdatedState(onDismissRequest)
    val navEventState = rememberNavigationEventState(NavigationEventInfo.None)
    val predictiveBackEnabled = rememberPredictiveBackEnabledState(prefs)
    val animationMode = rememberPredictiveBackAnimationModeState(prefs)
    val animationStyle = rememberPredictiveBackAnimationStyleState(prefs)
    val useConsistentAnimation = predictiveBackEnabled && animationMode == PredictiveBackAnimationMode.Consistent
    var latestGestureProgress by remember { mutableFloatStateOf(0f) }
    var latestGestureDirection by remember { mutableFloatStateOf(1f) }
    var completedBackProgress by remember { mutableFloatStateOf(0f) }
    var closingFromBackGesture by remember { mutableStateOf(false) }
    NavigationBackHandler(
        state = navEventState,
        isBackEnabled = visible,
        onBackCompleted = {
            completedBackProgress = if (predictiveBackEnabled) {
                latestGestureProgress.coerceIn(0f, 1f)
            } else {
                0f
            }
            val completedGestureEdge =
                (navEventState.transitionState as? NavigationEventTransitionState.InProgress)
                    ?.latestEvent
                    ?.swipeEdge
                    ?: EDGE_LEFT
            latestGestureDirection = predictiveBackExitDirection(completedGestureEdge == EDGE_LEFT)
            closingFromBackGesture = true
            currentOnDismissRequest()
        }
    )

    val transitionState = navEventState.transitionState
    val progressInProgress = if (predictiveBackEnabled) {
        transitionState as? NavigationEventTransitionState.InProgress
    } else {
        null
    }
    val gestureProgress = progressInProgress?.latestEvent?.progress ?: 0f
    val isGestureActive = progressInProgress != null
    val gestureEdge = progressInProgress?.latestEvent?.swipeEdge ?: EDGE_LEFT
    val activeGestureDirection = predictiveBackExitDirection(gestureEdge == EDGE_LEFT)
    val gestureDirection = if (isGestureActive) activeGestureDirection else latestGestureDirection
    LaunchedEffect(isGestureActive, gestureProgress) {
        if (isGestureActive) {
            latestGestureProgress = gestureProgress.coerceIn(0f, 1f)
        }
    }
    LaunchedEffect(visible) {
        if (visible) {
            completedBackProgress = 0f
            closingFromBackGesture = false
        }
    }

    val visibilityProgress by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "overlaySheetProgress"
    )
    val density = LocalDensity.current
    val backgroundShiftPx = with(density) { 12.dp.toPx() }
    val closeProgress = (1f - visibilityProgress).coerceIn(0f, 1f)
    val continuingBackClose = closingFromBackGesture && !visible && !isGestureActive
    val carriedDismissProgress = if (continuingBackClose) completedBackProgress else 0f
    val dismissProgress = when {
        isGestureActive -> gestureProgress
        !visible -> carriedDismissProgress + (1f - carriedDismissProgress) * closeProgress
        else -> 0f
    }.coerceIn(0f, 1f)
    val openingDismissProgress = (1f - visibilityProgress).coerceIn(0f, 1f)
    val predictiveDismissProgress = when {
        isGestureActive -> gestureProgress
        !visible -> dismissProgress
        else -> openingDismissProgress
    }.coerceIn(0f, 1f)
    val effectiveProgress = when {
        isGestureActive -> 1f - gestureProgress
        continuingBackClose -> 1f - dismissProgress
        else -> visibilityProgress
    }.coerceIn(0f, 1f)
    val usePredictiveAnimation = useConsistentAnimation && (visible || isGestureActive || visibilityProgress > 0.001f)
    val animationDirection = if (isGestureActive || continuingBackClose) gestureDirection else 1f

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (usePredictiveAnimation) {
                        applyPredictiveBackUnderlayTransform(
                            style = animationStyle,
                            dismissProgress = predictiveDismissProgress,
                            direction = animationDirection
                        )
                    } else {
                        translationY = -backgroundShiftPx * effectiveProgress
                    }
                }
        ) {
            content()

            if (effectiveProgress > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            scrimColor.copy(
                                alpha = if (usePredictiveAnimation) {
                                    predictiveBackUnderlayScrimAlpha(dismissProgress)
                                } else {
                                    0.08f * effectiveProgress
                                }
                            )
                        )
                )
            }
        }

        if (visible || isGestureActive || visibilityProgress > 0.001f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        if (isGestureActive) {
                            if (useConsistentAnimation) {
                                applyPredictiveBackFrontTransform(
                                    style = animationStyle,
                                    progress = gestureProgress,
                                    direction = animationDirection,
                                    pivotY = 0.5f
                                )
                            } else {
                                val sheetShrinkProgress = gestureProgress.coerceIn(0f, 1f)
                                scaleX = 1f - (0.12f * sheetShrinkProgress)
                                scaleY = 1f - (0.06f * sheetShrinkProgress)
                                translationY = size.height * 0.22f * sheetShrinkProgress
                                alpha = 1f - (0.08f * sheetShrinkProgress)
                            }
                        } else if (!visible) {
                            if (usePredictiveAnimation) {
                                applyPredictiveBackFrontTransform(
                                    style = animationStyle,
                                    progress = dismissProgress,
                                    direction = animationDirection,
                                    pivotY = 0.5f
                                )
                            } else {
                                val slideStart = 0.22f * carriedDismissProgress
                                val slideProgress = slideStart + (1f - slideStart) * closeProgress
                                scaleX = 1f - (0.12f * dismissProgress)
                                scaleY = 1f - (0.06f * dismissProgress)
                                translationY = size.height * slideProgress
                                alpha = 1f - (0.08f * dismissProgress)
                            }
                        } else {
                            if (usePredictiveAnimation) {
                                applyPredictiveBackFrontTransform(
                                    style = animationStyle,
                                    progress = openingDismissProgress,
                                    direction = animationDirection,
                                    pivotY = 0.5f
                                )
                            } else {
                                translationY = size.height * (1f - visibilityProgress)
                                alpha = 0.92f + 0.08f * visibilityProgress
                            }
                        }
                    }
            ) {
                sheetContent()
            }
        }
    }
}
