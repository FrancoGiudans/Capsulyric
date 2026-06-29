package com.example.islandlyrics.ui.navigation

import android.app.Activity
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
import com.example.islandlyrics.core.settings.AppPreferences
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
        AppPreferences.of(context)
    }
    val predictiveBackEnabled = rememberPredictiveBackEnabledState(prefs)
    val animationMode = rememberPredictiveBackAnimationModeState(prefs)
    val animationStyle = rememberPredictiveBackAnimationStyleState(prefs)
    val useConsistentAnimation = predictiveBackEnabled && animationMode == PredictiveBackAnimationMode.Consistent
    val isOverlaySheetTransition = closeExitTransition == R.anim.overlay_sheet_close_exit

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
    var lastPivotY by remember { mutableFloatStateOf(0.5f) }
    var lastProgress by remember { mutableFloatStateOf(0f) }
    var lastGestureDirection by remember { mutableFloatStateOf(1f) }

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
                    activity?.overrideActivityTransition(
                        Activity.OVERRIDE_TRANSITION_CLOSE,
                        R.anim.page_close_enter,
                        R.anim.page_close_enter
                    )
                }
            }
        )

        val transitionState = navEventState.transitionState
        val progressInProgress = transitionState as? NavigationEventTransitionState.InProgress
        val isGestureActive = progressInProgress != null

        val windowInfo = LocalWindowInfo.current
        val containerHeightPx = windowInfo.containerSize.height

        val edge = progressInProgress?.latestEvent?.swipeEdge ?: EDGE_LEFT
        val touchY = progressInProgress?.latestEvent?.touchY
        val gestureProgress = progressInProgress?.latestEvent?.progress ?: 0f

        if (isGestureActive) {
            lastProgress = gestureProgress
            lastPivotY = predictiveBackPivotY(touchY, containerHeightPx)
            lastGestureDirection = predictiveBackExitDirection(edge == EDGE_LEFT)
        }

        val currentPivotY = predictiveBackPivotY(touchY, containerHeightPx)

        val exitProgress = exitAnimatable.value
        val gestureDirection = predictiveBackExitDirection(edge == EDGE_LEFT)
        val exitDirection = lastGestureDirection

        val shouldAnimate = isGestureActive || isExiting

        Box(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (isExiting) {
                        val startProgress = lastProgress.coerceIn(0f, 1f)
                        val progress = startProgress + (1f - startProgress) * exitProgress
                        if (useConsistentAnimation) {
                            applyPredictiveBackFrontTransform(
                                style = animationStyle,
                                progress = progress,
                                direction = exitDirection,
                                pivotY = lastPivotY
                            )
                        } else {
                            applyPageSpecificActivityBackTransform(
                                progress = progress,
                                overlaySheet = isOverlaySheetTransition
                            )
                        }
                    } else if (isGestureActive) {
                        if (useConsistentAnimation) {
                            applyPredictiveBackFrontTransform(
                                style = animationStyle,
                                progress = gestureProgress,
                                direction = gestureDirection,
                                pivotY = currentPivotY
                            )
                        } else {
                            applyPageSpecificActivityBackTransform(
                                progress = gestureProgress,
                                overlaySheet = isOverlaySheetTransition
                            )
                        }
                    }
                }
                .clip(
                    if (shouldAnimate && useConsistentAnimation) RoundedCornerShape(16.dp)
                    else RoundedCornerShape(0.dp)
                )
        ) {
            currentContent()
        }
    }
}

private fun androidx.compose.ui.graphics.GraphicsLayerScope.applyPageSpecificActivityBackTransform(
    progress: Float,
    overlaySheet: Boolean
) {
    val p = progress.coerceIn(0f, 1f)
    alpha = 1f
    scaleX = 1f
    scaleY = 1f
    translationX = 0f
    translationY = 0f
    rotationX = 0f
    rotationY = 0f

    if (overlaySheet) {
        val sheetShrinkProgress = p.coerceIn(0f, 1f)
        scaleX = 1f - (0.12f * sheetShrinkProgress)
        scaleY = 1f - (0.06f * sheetShrinkProgress)
        translationY = size.height * 0.22f * sheetShrinkProgress
        alpha = 1f - (0.08f * sheetShrinkProgress)
    } else {
        translationX = size.width * p
        alpha = 1f - (0.04f * p)
    }
}
