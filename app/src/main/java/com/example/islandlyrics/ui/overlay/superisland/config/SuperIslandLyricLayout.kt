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

package com.example.islandlyrics.ui.overlay.superisland.config

import java.text.BreakIterator
import java.util.Locale
import com.example.islandlyrics.ui.overlay.superisland.calibration.RightTextWidthPolicy
import com.example.islandlyrics.ui.overlay.superisland.calibration.Glyphs

internal object SuperIslandLyricLayout {
    private const val FULL_LYRIC_LEFT_WITH_COVER_WEIGHT = 13
    private const val FULL_LYRIC_LEFT_NO_COVER_WEIGHT = 16
    private const val FULL_LYRIC_RIGHT_WEIGHT = 14
    private const val FULL_LYRIC_LEFT_NO_COVER_VISUAL_NUMERATOR = 5
    private const val FULL_LYRIC_LEFT_NO_COVER_VISUAL_DENOMINATOR = 6
    private const val FULL_LYRIC_LEFT_WITH_COVER_VISUAL_NUMERATOR = 6
    private const val FULL_LYRIC_LEFT_WITH_COVER_VISUAL_DENOMINATOR = 5
    private const val VISUAL_BALANCE_TOLERANCE_WEIGHT = 2
    private const val MAX_RIGHT_PADDING_SPACES = 4

    data class Split(val left: String, val right: String)

    fun splitFullLyric(
        text: String,
        showLeftCover: Boolean,
        leftMaxWeight: Int = if (showLeftCover) FULL_LYRIC_LEFT_WITH_COVER_WEIGHT else FULL_LYRIC_LEFT_NO_COVER_WEIGHT,
        rightMaxWeight: Int = FULL_LYRIC_RIGHT_WEIGHT,
        rightPolicy: RightTextWidthPolicy? = null
    ): Split {
        return splitBalancedByWeight(
            text = text,
            leftMaxWeight = leftMaxWeight,
            rightMaxWeight = rightMaxWeight,
            rightPolicy = rightPolicy,
            leftVisualNumerator = if (showLeftCover) {
                FULL_LYRIC_LEFT_WITH_COVER_VISUAL_NUMERATOR
            } else {
                FULL_LYRIC_LEFT_NO_COVER_VISUAL_NUMERATOR
            },
            leftVisualDenominator = if (showLeftCover) {
                FULL_LYRIC_LEFT_WITH_COVER_VISUAL_DENOMINATOR
            } else {
                FULL_LYRIC_LEFT_NO_COVER_VISUAL_DENOMINATOR
            }
        )
    }

    fun takeByWeight(text: String, maxWeight: Int): String {
        return extractTextByWeight(text.trim(), 0, maxWeight)
    }

    fun calculateWeight(text: String): Int = text.sumOf { charWeight(it) }

    private fun splitBalancedByWeight(
        text: String,
        leftMaxWeight: Int,
        rightMaxWeight: Int,
        rightPolicy: RightTextWidthPolicy?,
        leftVisualNumerator: Int,
        leftVisualDenominator: Int
    ): Split {
        val normalized = text.trim()
        if (normalized.isEmpty()) return Split("", "")

        val chars = normalized.toList()
        val boundaries = BreakIterator.getCharacterInstance(Locale.ROOT).apply { setText(normalized) }
        val fullFit = findBestSplit(
            chars = chars,
            boundaries = boundaries,
            endIndices = chars.size..chars.size,
            leftMaxWeight = leftMaxWeight,
            rightMaxWeight = rightMaxWeight,
            rightPolicy = rightPolicy,
            leftVisualNumerator = leftVisualNumerator,
            leftVisualDenominator = leftVisualDenominator
        )
        val best = fullFit ?: findBestSplit(
            chars = chars,
            boundaries = boundaries,
            endIndices = 2..chars.size,
            leftMaxWeight = leftMaxWeight,
            rightMaxWeight = rightMaxWeight,
            rightPolicy = rightPolicy,
            leftVisualNumerator = leftVisualNumerator,
            leftVisualDenominator = leftVisualDenominator
        )

        if (best == null) {
            val totalWeight = calculateWeight(normalized)
            val leftTarget = minOf(
                leftMaxWeight,
                maxOf(
                    1,
                    (totalWeight * leftVisualDenominator + leftVisualNumerator + leftVisualDenominator - 1) /
                        (leftVisualNumerator + leftVisualDenominator)
                )
            )
            val left = if (rightPolicy == null) extractTextByWeight(normalized, 0, leftTarget) else {
                val end = Glyphs.boundaries(normalized).lastOrNull {
                    calculateWeight(normalized.substring(0, it)) <= leftTarget
                } ?: 0
                normalized.substring(0, end)
            }
            val remainder = normalized.drop(left.length).let { if (rightPolicy == null) it.trimStart() else it }
            val right = rightPolicy?.prefix(remainder) ?: extractTextByWeight(remainder, 0, rightMaxWeight)
            return balanceWithRightPadding(left, right, leftVisualNumerator, leftVisualDenominator, rightPolicy)
        }

        return balanceWithRightPadding(
            left = chars.subList(0, best.splitIndex).joinToString("").trim(),
            right = chars.subList(best.splitIndex, best.endIndex).joinToString("").let {
                if (rightPolicy == null) it.trim() else it
            },
            leftVisualNumerator = leftVisualNumerator,
            leftVisualDenominator = leftVisualDenominator,
            rightPolicy = rightPolicy
        )
    }

    private data class Candidate(val splitIndex: Int, val endIndex: Int, val score: Int)

    private fun findBestSplit(
        chars: List<Char>,
        boundaries: BreakIterator,
        endIndices: IntRange,
        leftMaxWeight: Int,
        rightMaxWeight: Int,
        rightPolicy: RightTextWidthPolicy?,
        leftVisualNumerator: Int,
        leftVisualDenominator: Int
    ): Candidate? {
        var best: Candidate? = null
        val safeBoundaries = if (rightPolicy != null) Glyphs.boundaries(chars.joinToString("")).toHashSet() else null

        for (endIndex in endIndices) {
            if (!boundaries.isBoundary(endIndex)) continue
            if (safeBoundaries != null && endIndex !in safeBoundaries) continue
            for (splitIndex in 0..endIndex) {
                if (!boundaries.isBoundary(splitIndex)) continue
                if (safeBoundaries != null && splitIndex !in safeBoundaries) continue
                // Keep original separating spaces on the right instead of
                // trimming them off the end of the left column.
                if (rightPolicy != null && splitIndex > 0 && chars[splitIndex - 1].isWhitespace()) continue
                val left = chars.subList(0, splitIndex).joinToString("").trim()
                val rawRight = chars.subList(splitIndex, endIndex).joinToString("")
                val right = if (rightPolicy != null) rawRight else rawRight.trim()
                val leftWeight = calculateWeight(left)
                val rightWeight = calculateWeight(right)

                if (leftWeight > leftMaxWeight) continue
                if (rightPolicy != null) {
                    if (!rightPolicy.fits(right)) continue
                } else if (rightWeight > rightMaxWeight) continue

                val leftVisualWeight = scaleVisualWeight(leftWeight, leftVisualNumerator, leftVisualDenominator)

                val paddingSpaces = rightPaddingSpaces(leftVisualWeight, rightWeight)
                val paddedRightVisualWeight = rightWeight + paddingSpaces
                val overBalance = (leftVisualWeight - paddedRightVisualWeight - VISUAL_BALANCE_TOLERANCE_WEIGHT).coerceAtLeast(0)
                val underBalance = (paddedRightVisualWeight - leftVisualWeight - VISUAL_BALANCE_TOLERANCE_WEIGHT).coerceAtLeast(0)
                val usedWeight = leftWeight + rightWeight
                val balancePenalty = kotlin.math.abs(leftVisualWeight - paddedRightVisualWeight) * 10 +
                    overBalance * 80 +
                    underBalance * 20
                val unusedPenalty = (leftMaxWeight + rightMaxWeight - usedWeight)
                val edgePenalty = if (left.isEmpty() || right.isEmpty()) 20 else 0
                val score = balancePenalty + unusedPenalty + edgePenalty

                if (best == null || score < best.score) {
                    best = Candidate(splitIndex, endIndex, score)
                }
            }
        }
        return best
    }

    private fun scaleVisualWeight(weight: Int, numerator: Int, denominator: Int): Int {
        return (weight * numerator + denominator / 2) / denominator
    }

    private fun balanceWithRightPadding(
        left: String,
        right: String,
        leftVisualNumerator: Int,
        leftVisualDenominator: Int,
        rightPolicy: RightTextWidthPolicy? = null
    ): Split {
        if (left.isEmpty() || right.isEmpty()) return Split(left, right)

        val leftVisualWeight = scaleVisualWeight(
            calculateWeight(left),
            leftVisualNumerator,
            leftVisualDenominator
        )
        val rightWeight = calculateWeight(right)
        val paddingSpaces = rightPaddingSpaces(leftVisualWeight, rightWeight)
        return Split(left, rightPolicy?.padded(right, paddingSpaces) ?: (right + " ".repeat(paddingSpaces)))
    }

    private fun rightPaddingSpaces(leftVisualWeight: Int, rightWeight: Int): Int {
        return (leftVisualWeight - rightWeight).coerceIn(0, MAX_RIGHT_PADDING_SPACES)
    }

    private fun charWeight(c: Char): Int {
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

    private fun extractTextByWeight(text: String, startWeight: Int, maxWeight: Int): String {
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
}
