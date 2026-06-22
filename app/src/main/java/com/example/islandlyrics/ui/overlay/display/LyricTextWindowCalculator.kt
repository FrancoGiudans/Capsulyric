package com.example.islandlyrics.ui.overlay.display
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
