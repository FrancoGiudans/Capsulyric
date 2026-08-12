/*
 * This file is part of Capsulyric (IslandLyrics).
 * Portions of this file are derived from or based on compose-miuix-ui/miuix
 * (https://github.com/compose-miuix-ui/miuix), version 0.9.3.
 *
 * Original: Copyright 2025, compose-miuix-ui contributors
 * Original license: Apache License 2.0 (full text: LICENSES/Apache-2.0.txt)
 *
 * Upstream source: miuix: ScrollBehavior (basic) and overScrollVertical (utils)
 *
 * Modifications by FrancoGiudans for Capsulyric (IslandLyrics):
 *   - Scroll and haptic-effect modifiers built on miuix scroll APIs.
 */

/*
 *
 *  * Copyright (c) 2026 FrancoGiudans
 *  *
 *  * This file is part of Capsulyric.
 *  *
 *  * Capsulyric is free software: you can redistribute it and/or modify
 *  * it under the terms of the GNU General Public License as published by
 *  * the Free Software Foundation, either version 3 of the License, or
 *  * (at your option) any later version.
 *  *
 *  * Capsulyric is distributed in the hope that it will be useful,
 *  * but WITHOUT ANY WARRANTY; without even the implied warranty of
 *  * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *  * GNU General Public License for more details.
 *  *
 *  * You should have received a copy of the GNU General Public License
 *  * along with Capsulyric. If not, see <https://www.gnu.org/licenses/>.
 *
 *
 */

package com.example.islandlyrics.ui.miuix.effects

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
import com.example.islandlyrics.core.settings.AppPreferences
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
        AppPreferences.of(context)
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

