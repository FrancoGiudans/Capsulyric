package com.example.islandlyrics.ui.common

import android.app.Activity
import android.content.Context
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEvent.Companion.EDGE_LEFT
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.OnBackInvokedDefaultInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import android.os.Build
import com.example.islandlyrics.R
import kotlinx.coroutines.launch

@Composable
fun PredictiveBackActivity(
    enabled: Boolean = true,
    closeEnterTransition: Int = R.anim.page_close_enter,
    closeExitTransition: Int = R.anim.page_close_exit,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val prefs = remember {
        context.getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE)
    }
    val predictiveBackEnabled = rememberPredictiveBackEnabledState(prefs)
    val animationMode = rememberPredictiveBackAnimationModeState(prefs)
    val animationStyle = rememberPredictiveBackAnimationStyleState(prefs)
    val useConsistentAnimation = animationMode == PredictiveBackAnimationMode.Consistent

    if (!enabled || !predictiveBackEnabled) {
        content()
        return
    }
    val currentContent by rememberUpdatedState(content)
    val activityDispatcher = remember(activity) {
        NavigationEventDispatcher { activity?.finish() }
    }
    val activityDispatcherOwner = remember(activityDispatcher) {
        object : androidx.navigationevent.NavigationEventDispatcherOwner {
            override val navigationEventDispatcher = activityDispatcher
        }
    }
    DisposableEffect(activityDispatcher, activity) {
        if (activity != null && Build.VERSION.SDK_INT >= 33) {
            val input = OnBackInvokedDefaultInput(activity.onBackInvokedDispatcher)
            activityDispatcher.addInput(input)
            onDispose { activityDispatcher.removeInput(input) }
        } else {
            onDispose { }
        }
    }
    val navEventState = rememberNavigationEventState(NavigationEventInfo.None)
    val scope = rememberCoroutineScope()
    val exitAnimatable = remember { Animatable(0f) }
    var isExiting by remember { mutableStateOf(false) }
    var lastEdge by remember { mutableIntStateOf(0) }
    var lastPivotY by remember { mutableFloatStateOf(0.5f) }
    var lastProgress by remember { mutableFloatStateOf(0f) }

    CompositionLocalProvider(
        LocalNavigationEventDispatcherOwner provides activityDispatcherOwner
    ) {
        NavigationBackHandler(
            state = navEventState,
            isBackEnabled = true,
            onBackCompleted = {
                isExiting = true
                scope.launch {
                    exitAnimatable.animateTo(
                        targetValue = 1f,
                        animationSpec = tween(durationMillis = 150, easing = LinearEasing)
                    )
                    activity?.finish()
                    if (useConsistentAnimation) {
                        activity?.overrideActivityTransition(
                            Activity.OVERRIDE_TRANSITION_CLOSE,
                            R.anim.page_close_enter,
                            R.anim.page_close_enter
                        )
                    } else {
                        activity?.overrideActivityTransition(
                            Activity.OVERRIDE_TRANSITION_CLOSE,
                            closeEnterTransition,
                            closeExitTransition
                        )
                    }
                }
            }
        )

        val transitionState = navEventState.transitionState
        val progressInProgress = transitionState as? NavigationEventTransitionState.InProgress
        val isGestureActive = progressInProgress != null

        val windowInfo = LocalWindowInfo.current
        val containerHeightPx = windowInfo.containerSize.height

        val edge = progressInProgress?.latestEvent?.swipeEdge ?: 0
        val touchY = progressInProgress?.latestEvent?.touchY
        val gestureProgress = progressInProgress?.latestEvent?.progress ?: 0f
        val isLeftEdge = edge == EDGE_LEFT

        if (isGestureActive) {
            lastEdge = edge
            lastProgress = gestureProgress
            lastPivotY = predictiveBackPivotY(touchY, containerHeightPx)
        }

        val currentPivotY = predictiveBackPivotY(touchY, containerHeightPx)

        val exitProgress = exitAnimatable.value
        val gestureDirection = predictiveBackExitDirection(isLeftEdge)
        val exitDirection = predictiveBackExitDirection(lastEdge == EDGE_LEFT)

        val shouldAnimate = isGestureActive || isExiting

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (isExiting) {
                        val startProgress = lastProgress.coerceIn(0f, 1f)
                        val progress = startProgress + (1f - startProgress) * exitProgress
                        applyPredictiveBackFrontTransform(
                            style = if (useConsistentAnimation) {
                                animationStyle
                            } else {
                                PredictiveBackAnimationStyle.ScaleSlide
                            },
                            progress = progress,
                            direction = exitDirection,
                            pivotY = lastPivotY
                        )
                    } else if (isGestureActive) {
                        applyPredictiveBackFrontTransform(
                            style = if (useConsistentAnimation) {
                                animationStyle
                            } else {
                                PredictiveBackAnimationStyle.ScaleSlide
                            },
                            progress = gestureProgress,
                            direction = gestureDirection,
                            pivotY = currentPivotY
                        )
                    }
                }
                .clip(
                    if (shouldAnimate) RoundedCornerShape(16.dp)
                    else RoundedCornerShape(0.dp)
                )
        ) {
            currentContent()
        }
    }
}
