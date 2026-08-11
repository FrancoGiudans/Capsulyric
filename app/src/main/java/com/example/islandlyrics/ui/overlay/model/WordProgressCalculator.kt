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
 *  *
 *  *
 */

package com.example.islandlyrics.ui.overlay.model

import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher

/**
 * 逐字进度的共享计算器：
 * 全局显示循环与悬浮窗高频动画队列复用同一实现，保证两种刷新路径的进度一致。
 */
internal object WordProgressCalculator {
    fun compute(
        text: String,
        startTime: Long,
        endTime: Long,
        syllables: List<OnlineLyricFetcher.SyllableInfo>?,
        position: Long
    ): LyricPresentation.WordProgress? {
        val syllableList = syllables?.takeIf { it.isNotEmpty() } ?: return null
        val sungSyllables = syllableList.filter { it.startTime <= position }
        val lineDuration = endTime - startTime
        val lineProgress = if (lineDuration > 0) {
            ((position - startTime).toFloat() / lineDuration.toFloat()).coerceIn(0f, 1f)
        } else {
            0f
        }

        val syllableProgresses = buildList {
            var charOffset = 0
            syllableList.forEach { syllable ->
                val charStart = charOffset
                charOffset += syllable.text.length
                val charEnd = charOffset
                val progress = when {
                    position >= syllable.endTime -> 1f
                    position <= syllable.startTime -> 0f
                    else -> {
                        val duration = syllable.endTime - syllable.startTime
                        if (duration > 0) {
                            ((position - syllable.startTime).toFloat() / duration.toFloat())
                                .coerceIn(0f, 1f)
                        } else {
                            1f
                        }
                    }
                }
                add(
                    LyricPresentation.WordProgress.SyllableProgress(
                        text = syllable.text,
                        charStart = charStart,
                        charEnd = charEnd,
                        progress = progress
                    )
                )
            }
        }

        return LyricPresentation.WordProgress(
            sungText = sungSyllables.joinToString("") { it.text },
            sungSyllableCount = sungSyllables.size,
            totalSyllableCount = syllableList.size,
            lineProgress = lineProgress,
            syllables = syllableProgresses
        )
    }
}
