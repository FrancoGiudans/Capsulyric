package com.example.islandlyrics.ui.overlay.superisland.calibration

import java.util.regex.Pattern

/** Local calibration units: one ordinary CJK glyph occupies one unit. */
internal data class RightTextCalibration(
    val capacity: Float = 7f,
    val letterMonospace: Boolean = false,
    val letterWidth: Float = 0.5f,
    val letterScale: Float = 1f,
    val digitMonospace: Boolean = false,
    val digitWidth: Float = 0.5f,
    val digitScale: Float = 1f,
    val spaceWidth: Float = 0.25f,
    val fullSpaceWidth: Float = 1f,
    val punctuationScale: Float = 1f,
    val margin: Float = 0f,
    val overrides: Map<String, Float> = emptyMap()
) {
    fun sanitized() = copy(
        capacity = capacity.finiteIn(1f, 30f, 7f),
        letterWidth = letterWidth.finiteIn(0.05f, 3f, 0.5f),
        letterScale = letterScale.finiteIn(0.1f, 4f, 1f),
        digitWidth = digitWidth.finiteIn(0.05f, 3f, 0.5f),
        digitScale = digitScale.finiteIn(0.1f, 4f, 1f),
        spaceWidth = spaceWidth.finiteIn(0.05f, 3f, 0.25f),
        fullSpaceWidth = fullSpaceWidth.finiteIn(0.05f, 3f, 1f),
        punctuationScale = punctuationScale.finiteIn(0.1f, 4f, 1f),
        margin = margin.finiteIn(0f, capacity.finiteIn(1f, 30f, 7f) - 0.1f, 0f),
        overrides = overrides.entries.asSequence().filter {
            it.key.length <= 32 && Glyphs.parts(it.key).size == 1 && it.value.isFinite() && it.value > 0f
        }.take(128).associate { it.key to it.value.coerceIn(0.05f, 10f) }
    )
}

private fun Float.finiteIn(min: Float, max: Float, fallback: Float) =
    if (isFinite()) coerceIn(min, max) else fallback

internal object Glyphs {
    private val pattern = Pattern.compile("\\X")
    fun parts(text: String): List<String> = buildList {
        val matcher = pattern.matcher(text)
        while (matcher.find()) add(matcher.group())
    }

    fun boundaries(text: String): List<Int> = buildList {
        add(0)
        val matcher = pattern.matcher(text)
        while (matcher.find()) add(matcher.end())
    }

    fun limit(text: String, maxLength: Int): String =
        text.substring(0, boundaries(text).lastOrNull { it <= maxLength } ?: 0)
}

/** The measurer is an estimate for uncalibrated glyphs, not a SystemUI font oracle. */
internal class RightTextWidthPolicy(
    calibration: RightTextCalibration,
    private val estimate: (String) -> Float
) {
    val calibration = calibration.sanitized()
    val limit: Float get() = calibration.capacity - calibration.margin
    private val cache = object : LinkedHashMap<String, Float>(128, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, Float>?) = size > 256
    }

    fun width(text: String): Float = cache.getOrPut(text) {
        var total = 0f
        var run = StringBuilder()
        var runScale = 1f
        var runKind = -1
        fun flush() {
            if (run.isNotEmpty()) {
                val measured = estimate(run.toString())
                total += (if (measured.isFinite() && measured > 0f) measured else run.length.toFloat()) * runScale
                run = StringBuilder()
            }
        }
        for (glyph in Glyphs.parts(text)) {
            val cp = glyph.codePointAt(0)
            val script = Character.UnicodeScript.of(cp)
            val cjk = cp !in 0xFF61..0xFFDC && (script == Character.UnicodeScript.HAN ||
                script == Character.UnicodeScript.HIRAGANA || script == Character.UnicodeScript.KATAKANA ||
                script == Character.UnicodeScript.HANGUL)
            val letter = Character.isLetter(cp) && !cjk
            val digit = glyph.codePointCount(0, glyph.length) == 1 && Character.isDigit(cp)
            val fixed = calibration.overrides[glyph] ?: when {
                glyph == "\u3000" -> calibration.fullSpaceWidth
                glyph.all { it.isWhitespace() || Character.isSpaceChar(it) } -> calibration.spaceWidth
                cjk -> 1f
                letter && calibration.letterMonospace -> calibration.letterWidth
                digit && calibration.digitMonospace -> calibration.digitWidth
                else -> null
            }
            if (fixed != null) {
                flush()
                runKind = -1
                total += fixed
            } else {
                val kind = if (letter) 0 else if (digit) 1 else 2
                if (kind != runKind) flush()
                runKind = kind
                runScale = when (kind) {
                    0 -> calibration.letterScale
                    1 -> calibration.digitScale
                    else -> calibration.punctuationScale
                }
                run.append(glyph)
            }
        }
        flush()
        total
    }

    fun fits(text: String) = width(text) <= limit + 0.0001f

    fun prefix(text: String): String {
        var end = 0
        for (boundary in Glyphs.boundaries(text).drop(1)) {
            if (!fits(text.substring(0, boundary))) break
            end = boundary
        }
        return text.substring(0, end)
    }

    fun padded(text: String, requestedSpaces: Int): String {
        var result = text
        repeat(requestedSpaces.coerceIn(0, 4)) {
            if (fits(result + " ")) result += " "
        }
        return result
    }
}

/** All returned ranges share the same right-column fit predicate as final rendering. */
internal class CalibratedTextWindow(
    private val text: String,
    private val right: RightTextWidthPolicy,
    private val leftLimit: Int? = null,
    private val leftWeight: (String) -> Int = { 0 }
) {
    private val boundaries = Glyphs.boundaries(text)
    private val prefixWidths by lazy { boundaries.associateWith { right.width(text.substring(0, it)) } }
    private val windows = mutableMapOf<Int, String>()
    private val tail by lazy { boundaries.firstOrNull { fits(text.substring(it)) } ?: text.length }

    fun fits(candidate: String): Boolean {
        if (leftLimit == null) return right.fits(candidate)
        // Greedily putting the longest possible prefix in the unchanged left
        // column leaves the smallest possible suffix for the right column.
        var split = 0
        for (end in Glyphs.boundaries(candidate).drop(1)) {
            if (leftWeight(candidate.substring(0, end)) > leftLimit) break
            split = end
        }
        while (split > 0 && (candidate[split - 1].isWhitespace() || Character.isSpaceChar(candidate[split - 1]))) {
            split--
        }
        return right.fits(candidate.substring(split))
    }

    fun prefix() = fromIndex(0)

    fun tailStart(): Int = tail

    fun suffix() = text.substring(tailStart())

    fun nextIndex(index: Int): Int = boundaries.firstOrNull { it > index } ?: text.length

    fun fromIndex(index: Int): String {
        val start = boundaries.lastOrNull { it <= index.coerceIn(0, text.length) } ?: 0
        windows[start]?.let { return it }
        var end = start
        for (boundary in boundaries) {
            if (boundary <= start) continue
            if (!fits(text.substring(start, boundary))) break
            end = boundary
        }
        return text.substring(start, end).also { windows[start] = it }
    }

    fun atProgress(progress: Float): String {
        if (fits(text)) return text
        val tail = tailStart()
        val target = right.width(text.substring(0, tail)) * progress.coerceIn(0f, 1f)
        val start = boundaries.takeWhile { it <= tail }.minByOrNull {
            kotlin.math.abs(prefixWidths.getValue(it) - target)
        } ?: 0
        return fromIndex(start)
    }

    fun atFocus(focusIndex: Int, centered: Boolean): String {
        if (fits(text)) return text
        val tail = tailStart()
        if (focusIndex >= (boundaries.getOrNull(boundaries.lastIndex - 1) ?: 0)) return suffix()
        val totalLimit = right.limit + (leftLimit ?: 0) / 2f
        val target = (right.width(text.substring(0, focusIndex.coerceIn(0, text.length))) -
            totalLimit * if (centered) 0.5f else 1f).coerceAtLeast(0f)
        var start = boundaries.takeWhile { it <= tail }.minByOrNull {
            kotlin.math.abs(prefixWidths.getValue(it) - target)
        } ?: 0
        var result = fromIndex(start)
        // A wide grapheme may only fit in the left column. Move the window
        // forward until the focused grapheme is included instead of dropping it.
        while (start < focusIndex && start + result.length <= focusIndex) {
            start = nextIndex(start)
            result = fromIndex(start)
        }
        return result
    }
}
