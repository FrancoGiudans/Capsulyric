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

package com.example.islandlyrics.ui.overlay.display

import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher
import java.text.BreakIterator
import java.util.Locale

internal object LyricTextWindowCalculator {
    private const val DEFAULT_SHIFT_WEIGHT = 2
    private const val WESTERN_SCROLL_START_MIN_WEIGHT = 4
    private const val WESTERN_STATIC_RESERVE_MS = 750L
    private const val DEFAULT_STATIC_RESERVE_MS = 1_000L
    private const val BASE_FOCUS_DELAY_MS = 500L
    private const val MIN_SCROLL_DELAY_MS = 200L
    private const val MAX_SCROLL_DELAY_MS = 5_000L
    private const val DEFAULT_AVG_SHIFT_WEIGHT = 5

    fun charWeight(c: Char): Int {
        if (c.isWhitespace()) return 0
        return when (Character.UnicodeBlock.of(c)) {
            Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS,
            Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A,
            Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B,
            Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS,
            Character.UnicodeBlock.HIRAGANA,
            Character.UnicodeBlock.KATAKANA,
            Character.UnicodeBlock.HANGUL_SYLLABLES -> 2
            null -> 1
            else -> 1
        }
    }

    fun weight(text: String): Int = text.sumOf { charWeight(it) }

    fun isMostlyWestern(text: String): Boolean {
        val nonWhitespaceChars = text.count { !it.isWhitespace() }
        if (nonWhitespaceChars == 0) return false
        return text.count { !it.isWhitespace() && charWeight(it) == 2 } <= nonWhitespaceChars / 2
    }

    fun scrollStartThreshold(text: String, maxDisplayWeight: Int): Int {
        return if (isMostlyWestern(text)) {
            (maxDisplayWeight / 2).coerceAtLeast(WESTERN_SCROLL_START_MIN_WEIGHT)
        } else {
            maxDisplayWeight
        }
    }

    fun extractByWeight(text: String, startWeight: Int, maxWeight: Int): String {
        var currentWeight = 0
        var startIndex = 0
        var endIndex = 0
        for (i in text.indices) {
            if (currentWeight >= startWeight) {
                startIndex = i
                break
            }
            currentWeight += charWeight(text[i])
        }
        while (startIndex < text.length && text[startIndex].isWhitespace()) {
            startIndex++
        }
        currentWeight = 0
        for (i in startIndex until text.length) {
            currentWeight += charWeight(text[i])
            if (currentWeight > maxWeight) break
            endIndex = i + 1
        }
        if (endIndex <= startIndex) return ""
        return text.substring(startIndex, endIndex).trim()
    }

    fun syllableWindow(
        fullText: String,
        sungText: String,
        maxDisplayWeight: Int
    ): String {
        val fullWeight = weight(fullText)
        if (fullWeight <= maxDisplayWeight) return fullText

        val sungWeight = weight(sungText)
        val scrollStartThreshold = scrollStartThreshold(fullText, maxDisplayWeight)
        val maxOffset = maxOf(0, fullWeight - maxDisplayWeight)
        val targetOffset = maxOf(0, sungWeight - scrollStartThreshold)
        val finalOffset = minOf(targetOffset, maxOffset)
        return extractByWeight(fullText, finalOffset, maxDisplayWeight)
    }

    /**
     * Returns a width-limited lyric window centered on the syllable active at [position].
     * Returns null when the syllable text cannot be aligned with [fullText].
     */
    fun visualCenterSyllableWindow(
        fullText: String,
        syllables: List<OnlineLyricFetcher.SyllableInfo>?,
        position: Long,
        maxDisplayWeight: Int
    ): String? {
        if (maxDisplayWeight <= 0) return null
        if (weight(fullText) <= maxDisplayWeight) return fullText

        val nonWhitespaceOffsets = fullText.indices.filterNot { fullText[it].isWhitespace() }
        val normalizedText = buildString(nonWhitespaceOffsets.size) {
            nonWhitespaceOffsets.forEach { append(fullText[it]) }
        }
        val alignedSyllables = syllables
            ?.asSequence()
            ?.filter { it.text.any { char -> !char.isWhitespace() } }
            ?.toList()
            ?.takeIf { it.isNotEmpty() }
            ?: return null

        var normalizedOffset = 0
        var focusedRange: IntRange? = null
        alignedSyllables.forEach { syllable ->
            val normalizedSyllable = syllable.text.filterNot { it.isWhitespace() }
            if (normalizedSyllable.isEmpty() ||
                normalizedOffset + normalizedSyllable.length > normalizedText.length ||
                !normalizedText.regionMatches(normalizedOffset, normalizedSyllable, 0, normalizedSyllable.length)
            ) {
                return null
            }

            val range = nonWhitespaceOffsets[normalizedOffset]..nonWhitespaceOffsets[
                normalizedOffset + normalizedSyllable.length - 1
            ]
            if (position >= syllable.startTime) focusedRange = range
            normalizedOffset += normalizedSyllable.length
        }
        if (normalizedOffset != normalizedText.length) return null

        val range = focusedRange
        val focusWeight = if (range == null) {
            0.0
        } else {
            weight(fullText.substring(0, range.first)) +
                weight(fullText.substring(range.first, range.last + 1)) / 2.0
        }
        val totalWeight = weight(fullText)
        val maxStartWeight = maxOf(0, totalWeight - maxDisplayWeight)
        val targetStartWeight =
            (focusWeight - maxDisplayWeight / 2.0).coerceIn(0.0, maxStartWeight.toDouble())

        var bestStartIndex = 0
        var bestStartWeight = 0
        var bestDistance = kotlin.math.abs(targetStartWeight)
        val boundaries = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(fullText) }
        var segmentStart = boundaries.first()
        var prefixWeight = 0
        var segmentEnd = boundaries.next()
        while (segmentEnd != BreakIterator.DONE) {
            // The first boundary at or past maxStartWeight is the earliest
            // suffix that fits. Pin to it at the tail, even when rounding
            // backwards would preserve more context but hide the last glyph.
            val isTailBoundary = prefixWeight >= maxStartWeight
            val pinToTail = isTailBoundary && targetStartWeight == maxStartWeight.toDouble()
            val distance = kotlin.math.abs(prefixWeight - targetStartWeight)
            if (pinToTail || distance < bestDistance ||
                (distance == bestDistance && prefixWeight < bestStartWeight)
            ) {
                bestStartIndex = segmentStart
                bestStartWeight = prefixWeight
                bestDistance = distance
            }
            if (isTailBoundary) break
            prefixWeight += weight(fullText.substring(segmentStart, segmentEnd))
            segmentStart = segmentEnd
            segmentEnd = boundaries.next()
        }

        var endIndex = bestStartIndex
        var windowWeight = 0
        boundaries.setText(fullText)
        var nextBoundary = boundaries.following(bestStartIndex)
        while (nextBoundary != BreakIterator.DONE) {
            val segmentWeight = weight(fullText.substring(endIndex, nextBoundary))
            if (windowWeight + segmentWeight > maxDisplayWeight) break
            windowWeight += segmentWeight
            endIndex = nextBoundary
            nextBoundary = boundaries.next()
        }
        if (endIndex <= bestStartIndex) return null
        return fullText.substring(bestStartIndex, endIndex).trim()
    }

    fun smartShiftWeight(text: String, currentOffset: Int): Int {
        val segment = extractByWeight(text, currentOffset, 10)
        if (segment.isEmpty()) return DEFAULT_SHIFT_WEIGHT

        val nonWhitespaceChars = segment.count { !it.isWhitespace() }
        val cjkCount = segment.count { !it.isWhitespace() && charWeight(it) == 2 }
        val isCjk = nonWhitespaceChars > 0 && cjkCount > nonWhitespaceChars / 2

        return if (isCjk) {
            if (segment.isNotEmpty()) charWeight(segment[0]) else DEFAULT_SHIFT_WEIGHT
        } else {
            DEFAULT_SHIFT_WEIGHT
        }
    }

    fun adaptiveDelay(
        text: String,
        lyricDurations: List<Long>,
        defaultDelayMs: Long
    ): Long {
        if (lyricDurations.isEmpty()) return defaultDelayMs

        val avgDuration = lyricDurations.average().toLong()
        val avgLyricWeight = weight(text)
        val staticReserve = if (isMostlyWestern(text)) WESTERN_STATIC_RESERVE_MS else DEFAULT_STATIC_RESERVE_MS

        if (avgLyricWeight == 0 || avgDuration < staticReserve) return defaultDelayMs

        val estimatedSteps = maxOf(1, avgLyricWeight / DEFAULT_AVG_SHIFT_WEIGHT)
        val availableTime = avgDuration - staticReserve - (estimatedSteps * BASE_FOCUS_DELAY_MS)
        val timePerUnit = if (availableTime > 0 && avgLyricWeight > 0) {
            availableTime / avgLyricWeight
        } else {
            100L
        }

        val calculatedDelay = BASE_FOCUS_DELAY_MS + (timePerUnit * DEFAULT_AVG_SHIFT_WEIGHT)
        return calculatedDelay.coerceIn(MIN_SCROLL_DELAY_MS, MAX_SCROLL_DELAY_MS)
    }
}
