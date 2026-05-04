package com.example.islandlyrics.ui.common

object SuperIslandLyricLayout {
    private const val FULL_LYRIC_LEFT_WITH_COVER_WEIGHT = 13
    private const val FULL_LYRIC_LEFT_NO_COVER_WEIGHT = 16
    private const val FULL_LYRIC_RIGHT_WEIGHT = 14
    private const val FULL_LYRIC_LEFT_VISUAL_NUMERATOR = 5
    private const val FULL_LYRIC_LEFT_VISUAL_DENOMINATOR = 6

    data class Split(val left: String, val right: String)

    fun splitFullLyric(text: String, showLeftCover: Boolean): Split {
        return splitBalancedByWeight(
            text = text,
            leftMaxWeight = if (showLeftCover) FULL_LYRIC_LEFT_WITH_COVER_WEIGHT else FULL_LYRIC_LEFT_NO_COVER_WEIGHT,
            rightMaxWeight = FULL_LYRIC_RIGHT_WEIGHT,
            leftVisualNumerator = FULL_LYRIC_LEFT_VISUAL_NUMERATOR,
            leftVisualDenominator = FULL_LYRIC_LEFT_VISUAL_DENOMINATOR
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
        leftVisualNumerator: Int,
        leftVisualDenominator: Int
    ): Split {
        val normalized = text.trim()
        if (normalized.isEmpty()) return Split("", "")

        val chars = normalized.toList()
        var bestSplit = 1
        var bestScore = Int.MAX_VALUE

        for (splitIndex in 1..chars.size) {
            val left = chars.subList(0, splitIndex).joinToString("").trim()
            val right = chars.subList(splitIndex, chars.size).joinToString("").trim()
            val leftWeight = calculateWeight(left)
            val rightWeight = calculateWeight(right)

            if (leftWeight > leftMaxWeight || rightWeight > rightMaxWeight) continue

            val leftVisualWeight = (leftWeight * leftVisualNumerator) / leftVisualDenominator
            if (leftVisualWeight < rightWeight) continue

            val usedWeight = leftWeight + rightWeight
            val balancePenalty = kotlin.math.abs(leftVisualWeight - rightWeight) * 4
            val unusedPenalty = (leftMaxWeight + rightMaxWeight - usedWeight) * 2
            val edgePenalty = if (left.isEmpty() || right.isEmpty()) 20 else 0
            val score = balancePenalty + unusedPenalty + edgePenalty

            if (score < bestScore) {
                bestScore = score
                bestSplit = splitIndex
            }
        }

        if (bestScore == Int.MAX_VALUE) {
            val totalWeight = calculateWeight(normalized)
            val leftTarget = minOf(
                leftMaxWeight,
                maxOf(
                    1,
                    (totalWeight * leftVisualDenominator + leftVisualNumerator + leftVisualDenominator - 1) /
                        (leftVisualNumerator + leftVisualDenominator)
                )
            )
            val left = extractTextByWeight(normalized, 0, leftTarget)
            val right = extractTextByWeight(normalized.drop(left.length).trimStart(), 0, rightMaxWeight)
            return Split(left, right)
        }

        return Split(
            left = chars.subList(0, bestSplit).joinToString("").trim(),
            right = chars.subList(bestSplit, chars.size).joinToString("").trim()
        )
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
