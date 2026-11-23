/*
 * Copyright (c) 2026 FrancoGiudans
 * SPDX-License-Identifier: GPL-3.0-or-later
 */

package com.example.islandlyrics.feature.main.miuix

import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher.LyricLine
import com.example.islandlyrics.ui.overlay.model.SecondaryTextResolver

internal const val LyricShareCharacterLimit = 300
internal const val LyricShareLineLimit = 6

internal enum class LyricSelectionLimit { Lines, Characters }

private val ParagraphBreaks = Regex("[\\r\\n\\u0085\\u2028\\u2029]+")

internal data class LyricShareLine(val original: String, val translation: String?) {
    val characterCount: Int
        get() = original.codePointCount(0, original.length) +
            (translation?.let { it.codePointCount(0, it.length) } ?: 0)

    fun asParagraph(): String = listOfNotNull(original, translation).joinToString("\n")
}

internal class LyricSelection private constructor(
    private val timeline: List<LyricLine>,
    val cards: List<LyricShareLine>,
    val initialIndex: Int,
    val indices: Set<Int>,
    val characterCount: Int,
) {
    val canExport: Boolean
        get() = indices.size in 1..LyricShareLineLimit && characterCount <= LyricShareCharacterLimit

    fun limitWhenAdding(index: Int): LyricSelectionLimit? = when {
        indices.size >= LyricShareLineLimit -> LyricSelectionLimit.Lines
        cards[index].characterCount > LyricShareCharacterLimit - characterCount -> LyricSelectionLimit.Characters
        else -> null
    }

    fun toggle(index: Int): LyricSelection {
        val count = cards[index].characterCount
        return when {
            index in indices -> LyricSelection(timeline, cards, initialIndex, indices - index, characterCount - count)
            limitWhenAdding(index) == null -> LyricSelection(timeline, cards, initialIndex, indices + index, characterCount + count)
            else -> this
        }
    }

    fun selectedLines(): List<LyricShareLine> = indices.sorted().map(cards::get)

    fun loopRange(duration: Long): LyricLoopRange? =
        indices.singleOrNull()?.let { LyricLoopRange.from(timeline[it], duration) }

    companion object {
        fun start(timeline: List<LyricLine>, index: Int): LyricSelection {
            val cards = timeline.map { line ->
                val original = line.text.asShareText()
                val translation = line.translation?.asShareText()?.takeUnless {
                    it == original || SecondaryTextResolver.isPlaceholder(it)
                }
                LyricShareLine(original, translation)
            }
            return LyricSelection(timeline, cards, index, setOf(index), cards[index].characterCount)
        }

        private fun String.asShareText(): String = replace(ParagraphBreaks, " ").trim()
    }
}
