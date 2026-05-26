package com.example.islandlyrics.ui.common

import android.os.Build
import android.view.View
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.navigationevent.NavigationEventDispatcher
import androidx.navigationevent.NavigationEventInfo
import androidx.navigationevent.NavigationEventTransitionState
import androidx.navigationevent.OnBackInvokedDefaultInput
import androidx.navigationevent.compose.LocalNavigationEventDispatcherOwner
import androidx.navigationevent.compose.NavigationBackHandler
import androidx.navigationevent.compose.rememberNavigationEventState

@Composable
fun PredictiveBackDialogWrapper(
    onDismiss: () -> Unit,
    animationDirection: DialogBackAnimation = DialogBackAnimation.ScaleToCenter,
    onGestureProgressChanged: (isActive: Boolean, progress: Float) -> Unit = { _, _ -> },
    content: @Composable () -> Unit
) {
    val dialogView = LocalView.current
    val dialogDispatcher = remember { NavigationEventDispatcher() }
    val dialogDispatcherOwner = remember(dialogDispatcher) {
        object : androidx.navigationevent.NavigationEventDispatcherOwner {
            override val navigationEventDispatcher = dialogDispatcher
        }
    }
    DisposableEffect(dialogView) {
        if (Build.VERSION.SDK_INT >= 33) {
            val backDispatcher = dialogView.findOnBackInvokedDispatcher()
            if (backDispatcher != null) {
                val input = OnBackInvokedDefaultInput(backDispatcher)
                dialogDispatcher.addInput(input)
                onDispose { dialogDispatcher.removeInput(input) }
            } else {
                onDispose { }
            }
        } else {
            onDispose { }
        }
    }

    val navEventState = rememberNavigationEventState(NavigationEventInfo.None)
    val currentDismiss by rememberUpdatedState(onDismiss)

    CompositionLocalProvider(
        LocalNavigationEventDispatcherOwner provides dialogDispatcherOwner
    ) {
        NavigationBackHandler(
            state = navEventState,
            isBackEnabled = true,
            onBackCompleted = { currentDismiss() }
        )
    }

    val transitionState = navEventState.transitionState
    val progressInProgress = transitionState as? NavigationEventTransitionState.InProgress
    val gestureProgress = progressInProgress?.latestEvent?.progress ?: 0f
    val isGestureActive = progressInProgress != null
    onGestureProgressChanged(isGestureActive, gestureProgress)

    Box(
        modifier = Modifier.graphicsLayer {
            if (isGestureActive) {
                when (animationDirection) {
                    DialogBackAnimation.SlideDown -> {
                        val maxTranslation = 200f * density
                        translationY = maxTranslation * gestureProgress
                        alpha = 1f - gestureProgress * 0.3f
                    }
                    DialogBackAnimation.ScaleToCenter -> {
                        val scale = 1f - gestureProgress * 0.15f
                        scaleX = scale
                        scaleY = scale
                        alpha = 1f - gestureProgress * 0.3f
                    }
                    DialogBackAnimation.None -> Unit
                }
            }
        }
    ) {
        content()
    }
}

enum class DialogBackAnimation {
    None,
    SlideDown,
    ScaleToCenter
}
