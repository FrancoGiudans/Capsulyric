package com.example.islandlyrics.ui.common

object SuperIslandLyricLayout {
    private const val FULL_LYRIC_LEFT_WITH_COVER_WEIGHT = 13
    private const val FULL_LYRIC_LEFT_NO_COVER_WEIGHT = 16
    private const val FULL_LYRIC_RIGHT_WEIGHT = 14
    private const val FULL_LYRIC_LEFT_VISUAL_NUMERATOR = 5
    private const val FULL_LYRIC_LEFT_VISUAL_DENOMINATOR = 6
    private const val VISUAL_BALANCE_TOLERANCE_WEIGHT = 2
    private const val MAX_RIGHT_PADDING_SPACES = 4

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
        val fullFit = findBestSplit(
            chars = chars,
            endIndices = chars.size..chars.size,
            leftMaxWeight = leftMaxWeight,
            rightMaxWeight = rightMaxWeight,
            leftVisualNumerator = leftVisualNumerator,
            leftVisualDenominator = leftVisualDenominator
        )
        val best = fullFit ?: findBestSplit(
            chars = chars,
            endIndices = 2..chars.size,
            leftMaxWeight = leftMaxWeight,
            rightMaxWeight = rightMaxWeight,
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
            val left = extractTextByWeight(normalized, 0, leftTarget)
            val right = extractTextByWeight(normalized.drop(left.length).trimStart(), 0, rightMaxWeight)
            return balanceWithRightPadding(left, right, leftVisualNumerator, leftVisualDenominator)
        }

        return balanceWithRightPadding(
            left = chars.subList(0, best.splitIndex).joinToString("").trim(),
            right = chars.subList(best.splitIndex, best.endIndex).joinToString("").trim(),
            leftVisualNumerator = leftVisualNumerator,
            leftVisualDenominator = leftVisualDenominator
        )
    }

    private data class Candidate(val splitIndex: Int, val endIndex: Int, val score: Int)

    private fun findBestSplit(
        chars: List<Char>,
        endIndices: IntRange,
        leftMaxWeight: Int,
        rightMaxWeight: Int,
        leftVisualNumerator: Int,
        leftVisualDenominator: Int
    ): Candidate? {
        var best: Candidate? = null

        for (endIndex in endIndices) {
            for (splitIndex in 1 until endIndex) {
                val left = chars.subList(0, splitIndex).joinToString("").trim()
                val right = chars.subList(splitIndex, endIndex).joinToString("").trim()
                val leftWeight = calculateWeight(left)
                val rightWeight = calculateWeight(right)

                if (leftWeight > leftMaxWeight || rightWeight > rightMaxWeight) continue

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
        leftVisualDenominator: Int
    ): Split {
        if (left.isEmpty() || right.isEmpty()) return Split(left, right)

        val leftVisualWeight = scaleVisualWeight(
            calculateWeight(left),
            leftVisualNumerator,
            leftVisualDenominator
        )
        val rightWeight = calculateWeight(right)
        val paddingSpaces = rightPaddingSpaces(leftVisualWeight, rightWeight)
        return Split(left, right + " ".repeat(paddingSpaces))
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
