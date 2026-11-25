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

package com.example.islandlyrics.lyrics.online.selection

import com.example.islandlyrics.lyrics.online.parser.LyricChineseNormalizer
import java.text.Normalizer
import java.util.Locale

/**
 * Shared text normalization and tokenization rules for online lyric matching.
 *
 * Used consistently across [CandidateMatcher], [OnlineLyricSelector],
 * [VersionTagDetector], and `OnlineLyricSidecarMerger` so title/artist/album
 * equivalence never drifts between stages.
 */
internal object MatchNormalizer {

    // Only strip parenthesized/bracketed subtitles (...), [...], （...）, 【...】.
    // Never strip CJK corner quotes 「...」 / 『...』 because Japanese/Chinese
    // titles frequently use them for the core song name itself (e.g. 「僕は...」).
    private val INNERMOST_BRACKET_SEGMENT_REGEX = Regex(
        """\([^()]*\)|\[[^\[\]]*]|（[^（）]*）|【[^【】]*】"""
    )

    private val TRAILING_DASH_VERSION_REGEX = Regex(
        """\s*[-–—]\s*(?:from\s+the\s+first\s+take|the\s+first\s+take|live|remix(?:ed)?|remaster(?:ed)?(?:\s+\d{4})?|acoustic|radio\s+(?:edit|version)|instrumental|karaoke|cover|off[\s-]?vocal|version|ver\.?(?:\s*[\w.]+)?|tv\s*size|short\s*ver\.?|full\s*ver\.?|现场版?|演唱会|纯音乐|伴奏|翻唱|混音|不插电|电台版)\s*$""",
        RegexOption.IGNORE_CASE
    )

    private val STANDALONE_SUFFIX_REGEX = Regex(
        """(?i)(?:^|[\s\-_/])(?:feat\.?|ft\.?|featuring|remix(?:ed)?|remaster(?:ed)?|version|ver\.?|live|cover|radio\s+edit|off[\s-]?vocal|instrumental|karaoke|acoustic)(?=$|[\s\-_/.,!?:;])"""
    )

    private val ARTIST_BRACKET_REGEX = Regex(
        """\(([^)]+)\)|\[([^\]]+)]|（([^）]+)）|【([^】]+)】"""
    )

    private val ARTIST_PREFIX_REGEX = Regex(
        """^(?:feat\.?|ft\.?|featuring|with|cv[:.\s]*|vocals?\s+by|by)\s+""",
        RegexOption.IGNORE_CASE
    )

    private val ARTIST_SPLIT_REGEX = Regex(
        """[/&,、;+×✕]|\s+(?:feat\.?|ft\.?|featuring|with|and|x)\s+""",
        RegexOption.IGNORE_CASE
    )

    private val NON_WORD_PUNCT_REGEX = Regex("""[^\p{L}\p{N}]+""")
    private val MULTI_SPACE_REGEX = Regex("""\s+""")

    /**
     * Normalizes text for identity comparison without destroying Japanese kana
     * dakuten/handakuten marks.
     */
    fun normalizeText(value: String): String {
        if (value.isBlank()) return ""
        val nfkc = Normalizer.normalize(value, Normalizer.Form.NFKC)
        val simplified = LyricChineseNormalizer.toSimplified(nfkc)
        val sb = StringBuilder(simplified.length)
        for (ch in simplified) {
            when (ch) {
                '’', '‘', 'ʼ', '`' -> sb.append('\'')
                '“', '”' -> sb.append('"')
                '—', '–', '－' -> sb.append('-')
                '～', '〜' -> sb.append('~')
                '・', '·', '•' -> sb.append(' ')
                else -> appendWithoutLatinDiacritics(sb, ch)
            }
        }
        return sb.toString()
            .lowercase(Locale.ROOT)
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()
    }

    /**
     * Stronger normalization that folds punctuation away so `"It's My Life"`
     * matches `"Its My Life"` or `"A - B"` matches `"A B"`.
     */
    fun normalizeCompact(value: String): String {
        val normalized = normalizeText(value)
        if (normalized.isBlank()) return ""
        return normalized
            .replace(NON_WORD_PUNCT_REGEX, " ")
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()
    }

    /**
     * Strips bracketed subtitles and standalone version/feat markers using word
     * boundaries so words like "Alive", "Discovery", "Recovery", or "HOYO-MiX"
     * are never corrupted.
     */
    fun cleanTitle(title: String): String {
        val trimmed = title.trim()
        if (trimmed.isBlank()) return ""

        var clean = trimmed
        repeat(3) {
            val next = clean.replace(INNERMOST_BRACKET_SEGMENT_REGEX, " ")
            if (next == clean) return@repeat
            clean = next
        }
        clean = clean.replace(TRAILING_DASH_VERSION_REGEX, " ")
        clean = clean.replace(STANDALONE_SUFFIX_REGEX, " ")
        clean = clean.replace(MULTI_SPACE_REGEX, " ").trim()
        // If stripping brackets emptied the whole title (e.g. "(Song)"), fall back to inner text.
        if (clean.isBlank()) {
            clean = trimmed
                .replace(Regex("""[()\[\]（）【】]"""), " ")
                .replace(MULTI_SPACE_REGEX, " ")
                .trim()
        }
        return clean
    }

    /**
     * Extracts the primary CJK title from an unbracketed bilingual title such as
     * `"无中生有 Ex Nihilo"` -> `"无中生有"` or `"未行之路 The Road Not Taken"` -> `"未行之路"`.
     * Returns null when the title is not a CJK + Latin bilingual pair.
     */
    fun extractBilingualPrimaryTitle(title: String): String? {
        val cleaned = cleanTitle(title)
        if (cleaned.isBlank()) return null

        var firstCjkSeen = false
        var splitIndex = -1
        for (i in cleaned.indices) {
            val ch = cleaned[i]
            if (ch.isWhitespace()) continue
            if (isCjkOrKanaOrHangul(ch)) {
                if (splitIndex != -1) {
                    // CJK appeared again after Latin (e.g. "A 恋 B"), not a clean "CJK + English" suffix.
                    return null
                }
                firstCjkSeen = true
            } else if (firstCjkSeen && (ch in 'a'..'z' || ch in 'A'..'Z')) {
                if (splitIndex == -1) splitIndex = i
            } else if (!firstCjkSeen) {
                return null
            }
        }
        if (splitIndex <= 0) return null
        val cjkPart = cleaned.substring(0, splitIndex).trim()
        val latinPart = cleaned.substring(splitIndex).trim()
        val compactCjk = normalizeCompact(cjkPart).replace(" ", "")
        val compactLatin = normalizeCompact(latinPart).replace(" ", "")
        // Require substantive CJK primary title (>= 2 chars) and Latin translation (>= 3 letters)
        // so song names like "Q版" or "A梦" are never split.
        if (compactCjk.length < 2 || compactLatin.length < 3) return null
        return cjkPart
    }

    private fun isCjkOrKanaOrHangul(ch: Char): Boolean =
        ch in '\u3040'..'\u309F' ||
            ch in '\u30A0'..'\u30FF' ||
            ch in '\u1100'..'\u11FF' ||
            ch in '\u3130'..'\u318F' ||
            ch in '\uAC00'..'\uD7AF' ||
            ch in '\u3400'..'\u4DBF' ||
            ch in '\u4E00'..'\u9FFF' ||
            ch in '\uF900'..'\uFAFF'

    /**
     * Splits an artist string into normalized tokens, preserving parenthesized
     * featured artists and bilingual aliases (e.g. "陈奕迅 (Eason Chan)",
     * "DAOKO × 米津玄師", "Jay Chou (feat. Fei Yu-Ching)").
     */
    fun normalizeArtistTokens(artist: String): Set<String> {
        if (artist.isBlank()) return emptySet()
        val normalized = normalizeText(artist)
        if (normalized.isBlank()) return emptySet()

        val rawSegments = mutableListOf<String>()
        val outside = ARTIST_BRACKET_REGEX.replace(normalized) { match ->
            val inner = match.groupValues.drop(1).firstOrNull { it.isNotBlank() }.orEmpty()
            if (inner.isNotBlank()) {
                rawSegments += inner
            }
            " "
        }
        rawSegments += outside

        val tokens = linkedSetOf<String>()
        for (segment in rawSegments) {
            val cleanedSegment = segment
                .replace(ARTIST_PREFIX_REGEX, "")
                .trim()
            for (part in cleanedSegment.split(ARTIST_SPLIT_REGEX)) {
                val token = part
                    .replace(ARTIST_PREFIX_REGEX, "")
                    .trim { !it.isLetterOrDigit() }
                    .replace(MULTI_SPACE_REGEX, " ")
                    .trim()
                if (token.isNotBlank()) {
                    tokens += token
                }
            }
        }
        return tokens
    }

    data class ArtistOverlap(
        val targetMatched: Int,
        val candidateMatched: Int,
        val targetTotal: Int,
        val candidateTotal: Int
    ) {
        val hasOverlap: Boolean get() = targetMatched > 0 && candidateMatched > 0
        val isExactAll: Boolean get() =
            targetTotal > 0 && candidateTotal > 0 &&
                targetMatched == targetTotal && candidateMatched == candidateTotal
        val isSubsetMatch: Boolean get() =
            targetTotal > 0 && candidateTotal > 0 &&
                (targetMatched == targetTotal || candidateMatched == candidateTotal)
    }

    /**
     * Computes artist token overlap supporting exact equality, compact
     * punctuation-insensitive equality, and Japanese kana/romaji equivalence.
     */
    fun computeArtistOverlap(targetArtist: String, candidateArtist: String): ArtistOverlap {
        val targetTokens = normalizeArtistTokens(targetArtist)
        val candidateTokens = normalizeArtistTokens(candidateArtist)
        if (targetTokens.isEmpty() || candidateTokens.isEmpty()) {
            return ArtistOverlap(0, 0, targetTokens.size, candidateTokens.size)
        }

        val matchedCandidateIndices = mutableSetOf<Int>()
        var targetMatched = 0
        val candidateList = candidateTokens.toList()
        for (targetToken in targetTokens) {
            var matchedThisTarget = false
            for (idx in candidateList.indices) {
                if (tokensEquivalent(targetToken, candidateList[idx])) {
                    matchedThisTarget = true
                    matchedCandidateIndices += idx
                }
            }
            if (matchedThisTarget) targetMatched++
        }
        return ArtistOverlap(
            targetMatched = targetMatched,
            candidateMatched = matchedCandidateIndices.size,
            targetTotal = targetTokens.size,
            candidateTotal = candidateTokens.size
        )
    }

    /**
     * True when two titles use meaningfully different writing scripts
     * (for example Chinese vs Japanese kana, or CJK vs Latin), indicating a
     * plausible localized translation rather than two unrelated songs in the
     * same language.
     */
    fun hasScriptDifference(first: String, second: String): Boolean {
        val left = detectScriptProfile(normalizeCompact(first))
        val right = detectScriptProfile(normalizeCompact(second))
        if (left == ScriptProfile.OTHER || right == ScriptProfile.OTHER) return false
        return left != right
    }

    private enum class ScriptProfile {
        LATIN,
        CJK_HAN_ONLY,
        JAPANESE_KANA,
        KOREAN_HANGUL,
        OTHER
    }

    private fun detectScriptProfile(text: String): ScriptProfile {
        if (text.isBlank()) return ScriptProfile.OTHER
        var hasKana = false
        var hasHangul = false
        var hasHan = false
        var hasLatin = false
        for (ch in text) {
            when {
                ch in '\u3040'..'\u309F' || ch in '\u30A0'..'\u30FF' -> hasKana = true
                ch in '\u1100'..'\u11FF' || ch in '\u3130'..'\u318F' || ch in '\uAC00'..'\uD7AF' -> hasHangul = true
                ch in '\u3400'..'\u4DBF' || ch in '\u4E00'..'\u9FFF' || ch in '\uF900'..'\uFAFF' -> hasHan = true
                ch in 'a'..'z' || ch in 'A'..'Z' -> hasLatin = true
            }
        }
        return when {
            hasKana -> ScriptProfile.JAPANESE_KANA
            hasHangul -> ScriptProfile.KOREAN_HANGUL
            hasHan -> ScriptProfile.CJK_HAN_ONLY
            hasLatin -> ScriptProfile.LATIN
            else -> ScriptProfile.OTHER
        }
    }

    private fun tokensEquivalent(left: String, right: String): Boolean {
        if (left == right) return true
        val compactLeft = left.replace(NON_WORD_PUNCT_REGEX, "")
        val compactRight = right.replace(NON_WORD_PUNCT_REGEX, "")
        if (compactLeft.isNotBlank() && compactLeft == compactRight) return true
        if (JapaneseRomajiMatcher.areEquivalent(left, right)) return true
        return isBilingualOrAliasArtistContainment(left, right)
    }

    /**
     * Handles bilingual artist strings without brackets such as
     * `"G.E.M."` vs `"G.E.M. 邓紫棋"` or `"陈奕迅"` vs `"Eason Chan 陈奕迅"`.
     */
    private fun isBilingualOrAliasArtistContainment(left: String, right: String): Boolean {
        val (shorter, longer) = if (left.length <= right.length) left to right else right to left
        val compactShort = shorter.replace(NON_WORD_PUNCT_REGEX, "")
        if (compactShort.length < 2) return false
        val isPureLatin = compactShort.all { it in 'a'..'z' || it in '0'..'9' }
        if (isPureLatin) {
            if (compactShort.length < 3) return false
            val boundaryRegex = Regex("""(?<![a-z0-9])${Regex.escape(shorter)}(?![a-z0-9])""")
            if (boundaryRegex.containsMatchIn(longer)) return true
            val compactLong = longer.replace(NON_WORD_PUNCT_REGEX, "")
            return compactLong.startsWith(compactShort) || compactLong.endsWith(compactShort)
        }
        return longer.contains(shorter)
    }

    private fun appendWithoutLatinDiacritics(sb: StringBuilder, ch: Char) {
        val isExtendedLatin = ch in '\u00C0'..'\u024F' || ch in '\u1E00'..'\u1EFF'
        if (!isExtendedLatin) {
            sb.append(ch)
            return
        }
        val decomposed = Normalizer.normalize(ch.toString(), Normalizer.Form.NFKD)
        for (d in decomposed) {
            if (Character.getType(d) != Character.NON_SPACING_MARK.toInt()) {
                sb.append(d)
            }
        }
    }
}
