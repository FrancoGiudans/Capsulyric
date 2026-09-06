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

package com.example.islandlyrics.ui.miuix.search

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import kotlinx.coroutines.delay
import top.yukonga.miuix.kmp.theme.MiuixTheme

/**
 * 设置项高亮修饰符。
 * 当 targetKey 与 currentKey 匹配时，触发一次平滑的主色背景呼吸闪烁，并在动画结束后调用 onHighlightComplete。
 */
@Composable
fun Modifier.miuixSettingHighlight(
    targetKey: String?,
    currentKey: String,
    onHighlightComplete: () -> Unit = {}
): Modifier {
    val isTarget = targetKey != null && targetKey == currentKey
    val alpha = remember(targetKey, currentKey) { Animatable(0f) }
    val primaryColor = MiuixTheme.colorScheme.primary

    LaunchedEffect(isTarget) {
        if (isTarget) {
            // Pulse: 0 -> 0.35f -> 0.12f -> 0.35f -> 0f over ~1.6s
            alpha.animateTo(0.35f, tween(250))
            delay(200)
            alpha.animateTo(0.12f, tween(250))
            delay(150)
            alpha.animateTo(0.35f, tween(250))
            delay(300)
            alpha.animateTo(0f, tween(500))
            onHighlightComplete()
        }
    }

    return this.drawBehind {
        if (alpha.value > 0f) {
            drawRect(color = primaryColor.copy(alpha = alpha.value))
        }
    }
}
