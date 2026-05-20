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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.unit.dp
import androidx.navigationevent.NavigationEvent.Companion.EDGE_LEFT
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState
import kotlinx.coroutines.launch

@Composable
fun PredictiveBackActivity(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val activity = context as? Activity
    val prefs = remember {
        context.getSharedPreferences("IslandLyricsPrefs", Context.MODE_PRIVATE)
    }
    val enabled = remember { prefs.getBoolean("predictive_back_enabled", true) }

    if (!enabled) {
        content()
        return
    }
    val navEventState = rememberNavigationEventState(NavigationEventInfo.None)
    val scope = rememberCoroutineScope()
    val exitAnimatable = remember { Animatable(0f) }
    var isExiting by remember { mutableStateOf(false) }
    var lastEdge by remember { mutableIntStateOf(0) }
    var lastPivotY by remember { mutableFloatStateOf(0.5f) }
    var lastScale by remember { mutableFloatStateOf(1f) }

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
                activity?.overrideActivityTransition(Activity.OVERRIDE_TRANSITION_CLOSE, 0, 0)
            }
        }
    )

    val transitionState = navEventState.transitionState
    val progressInProgress = transitionState as? NavigationEventTransitionState.InProgress
    val isGestureActive = progressInProgress != null

    val windowInfo = LocalWindowInfo.current
    val containerHeightPx = windowInfo.containerSize.height
    val containerWidthPx = windowInfo.containerSize.width.toFloat()

    val edge = progressInProgress?.latestEvent?.swipeEdge ?: 0
    val touchY = progressInProgress?.latestEvent?.touchY
    val gestureProgress = progressInProgress?.latestEvent?.progress ?: 0f

    if (isGestureActive) {
        lastEdge = edge
        lastScale = 1f - (1f - 0.9f) * gestureProgress
        lastPivotY = if (touchY != null && containerHeightPx > 0) {
            (touchY / containerHeightPx).coerceIn(0.1f, 0.9f)
        } else 0.5f
    }

    val maxScale = 0.9f
    val dragScale = 1f - (1f - maxScale) * gestureProgress
    val currentPivotY = if (touchY != null && containerHeightPx > 0) {
        (touchY / containerHeightPx).coerceIn(0.1f, 0.9f)
    } else 0.5f
    val currentPivotX = if (edge == EDGE_LEFT) 0.8f else 0.2f

    val exitProgress = exitAnimatable.value
    val directionMultiplier = if (lastEdge == EDGE_LEFT) 1f else -1f

    val shouldAnimate = isGestureActive || isExiting

    Box(
        modifier = Modifier
            .fillMaxSize()
            .graphicsLayer {
                if (isExiting) {
                    val exitPivotX = if (lastEdge == EDGE_LEFT) 0.8f else 0.2f
                    scaleX = lastScale
                    scaleY = lastScale
                    translationX = containerWidthPx * directionMultiplier * exitProgress
                    transformOrigin = TransformOrigin(exitPivotX, lastPivotY)
                } else if (isGestureActive) {
                    scaleX = dragScale
                    scaleY = dragScale
                    transformOrigin = TransformOrigin(currentPivotX, currentPivotY)
                }
            }
            .clip(
                if (shouldAnimate) RoundedCornerShape(16.dp)
                else RoundedCornerShape(0.dp)
            )
    ) {
        content()
    }
}
