package com.example.islandlyrics.ui.miuix

import android.os.SystemClock
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.Velocity
import com.example.islandlyrics.core.settings.LabFeatureManager
import top.yukonga.miuix.kmp.basic.ScrollBehavior
import top.yukonga.miuix.kmp.utils.overScrollVertical

private const val EDGE_HAPTIC_SCROLL_THRESHOLD = 0.1f
private const val EDGE_HAPTIC_FLING_THRESHOLD = 1f
private const val EDGE_HAPTIC_DEBOUNCE_MS = 180L

private enum class EdgeHapticState {
    Idle,
    TopBoundaryHit,
    BottomBoundaryHit,
}

fun Modifier.miuixPageScroll(
    scrollBehavior: ScrollBehavior? = null,
    enableTopAppBarScroll: Boolean = true,
    enableFillMaxHeight: Boolean = false,
): Modifier = composed {
    val enableScrollEndHaptic = rememberLabScrollEndHapticEnabled()
    this
        .then(if (enableScrollEndHaptic) Modifier.edgeScrollEndHaptic() else Modifier)
        .overScrollVertical()
        .then(if (enableTopAppBarScroll && scrollBehavior != null) Modifier.nestedScroll(scrollBehavior.nestedScrollConnection) else Modifier)
        .then(if (enableFillMaxHeight) Modifier.fillMaxHeight() else Modifier)
}

private fun Modifier.edgeScrollEndHaptic(): Modifier = composed {
    val haptic = LocalHapticFeedback.current
    var edgeState by remember { mutableStateOf(EdgeHapticState.Idle) }
    var lastTriggerAt by remember { mutableStateOf(0L) }

    fun tryTrigger(direction: Int) {
        if (direction == 0) return
        val now = SystemClock.elapsedRealtime()
        if (now - lastTriggerAt < EDGE_HAPTIC_DEBOUNCE_MS) return
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        lastTriggerAt = now
    }

    val connection = remember(haptic) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (edgeState == EdgeHapticState.TopBoundaryHit && available.y < -EDGE_HAPTIC_SCROLL_THRESHOLD) {
                    edgeState = EdgeHapticState.Idle
                } else if (edgeState == EdgeHapticState.BottomBoundaryHit && available.y > EDGE_HAPTIC_SCROLL_THRESHOLD) {
                    edgeState = EdgeHapticState.Idle
                }
                return Offset.Zero
            }

            override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                if (source != NestedScrollSource.UserInput) return Offset.Zero

                if (available.y > EDGE_HAPTIC_SCROLL_THRESHOLD && edgeState != EdgeHapticState.TopBoundaryHit) {
                    tryTrigger(1)
                    edgeState = EdgeHapticState.TopBoundaryHit
                } else if (available.y < -EDGE_HAPTIC_SCROLL_THRESHOLD && edgeState != EdgeHapticState.BottomBoundaryHit) {
                    tryTrigger(-1)
                    edgeState = EdgeHapticState.BottomBoundaryHit
                }
                return Offset.Zero
            }

            override suspend fun onPostFling(consumed: Velocity, available: Velocity): Velocity {
                if (available.y > EDGE_HAPTIC_FLING_THRESHOLD && edgeState != EdgeHapticState.TopBoundaryHit) {
                    tryTrigger(1)
                    edgeState = EdgeHapticState.TopBoundaryHit
                } else if (available.y < -EDGE_HAPTIC_FLING_THRESHOLD && edgeState != EdgeHapticState.BottomBoundaryHit) {
                    tryTrigger(-1)
                    edgeState = EdgeHapticState.BottomBoundaryHit
                }
                return Velocity.Zero
            }
        }
    }

    Modifier.nestedScroll(connection)
}

@Composable
fun rememberLabScrollEndHapticEnabled(): Boolean {
    val context = LocalContext.current
    val prefs = remember(context) {
        context.getSharedPreferences("IslandLyricsPrefs", android.content.Context.MODE_PRIVATE)
    }
    var enabled by remember(prefs) {
        mutableStateOf(LabFeatureManager.isScrollEndHapticEnabled(prefs))
    }

    DisposableEffect(prefs) {
        val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == LabFeatureManager.KEY_SCROLL_END_HAPTIC_ENABLED) {
                enabled = LabFeatureManager.isScrollEndHapticEnabled(prefs)
            }
        }
        prefs.registerOnSharedPreferenceChangeListener(listener)
        onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
    }

    return enabled
}
