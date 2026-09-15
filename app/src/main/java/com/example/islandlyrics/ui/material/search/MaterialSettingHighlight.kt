/*
 * Copyright (c) 2026 FrancoGiudans
 *
 * This file is part of Capsulyric.
 */

package com.example.islandlyrics.ui.material.search

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import kotlinx.coroutines.delay

/** Briefly highlights a Material setting row after navigation from search. */
@Composable
fun Modifier.materialSettingHighlight(
    targetKey: String?,
    currentKey: String,
    onHighlightComplete: () -> Unit = {}
): Modifier {
    val isTarget = targetKey != null && targetKey == currentKey
    val alpha = remember(targetKey, currentKey) { Animatable(0f) }
    val primaryColor = MaterialTheme.colorScheme.primary

    LaunchedEffect(isTarget) {
        if (isTarget) {
            alpha.animateTo(0.28f, tween(250))
            delay(200)
            alpha.animateTo(0.10f, tween(250))
            delay(150)
            alpha.animateTo(0.28f, tween(250))
            delay(300)
            alpha.animateTo(0f, tween(500))
            onHighlightComplete()
        }
    }

    return drawBehind {
        if (alpha.value > 0f) {
            drawRect(color = primaryColor.copy(alpha = alpha.value))
        }
    }
}
