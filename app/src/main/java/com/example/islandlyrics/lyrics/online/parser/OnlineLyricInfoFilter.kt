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

// Credit-line heuristics inspired by Lyricify-Lyrics-Helper (InfoLines.cs / TtmlParser.cs / YrcParser.cs)
// (https://github.com/WXRIW/Lyricify-Lyrics-Helper)
// Copyright (C) WXRIW/Lyricify-Lyrics-Helper contributors
// Licensed under Apache-2.0

package com.example.islandlyrics.lyrics.online.parser

import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher
import org.json.JSONObject
import java.util.Locale

internal data class TrackFilterContext(
    val title: String = "",
    val artist: String = "",
    val rawTitle: String = "",
    val rawArtist: String = "",
    val matchedTitle: String? = null,
    val matchedArtist: String? = null
)

internal data class LyricCredits(
    val lyricists: List<String> = emptyList(),
    val composers: List<String> = emptyList(),
    val songwriters: List<String> = emptyList()
) {
    /**
     * Merges lyricists and composers (or uses explicit TTML/combined songwriters)
     * into an ordered, case-insensitively deduplicated list for `Written by: ...`.
     */
    fun mergedWriters(): List<String> {
        val raw = if (lyricists.isEmpty() && composers.isEmpty()) {
            songwriters
        } else {
            lyricists + composers + songwriters
        }
        return deduplicateNames(raw)
    }

    companion object {
        internal fun deduplicateNames(names: List<String>): List<String> {
            val seen = LinkedHashSet<String>()
            val result = mutableListOf<String>()
            for (rawName in names) {
                val cleaned = rawName.trim()
                if (cleaned.isBlank()) continue
                val key = cleaned.lowercase(Locale.ROOT).replace(MULTI_SPACE_REGEX, " ")
                if (seen.add(key)) {
                    result.add(cleaned)
                }
            }
            return result
        }

        private val MULTI_SPACE_REGEX = Regex("\\s+")
    }
}

internal data class InfoFilterResult(
    val lines: List<OnlineLyricFetcher.LyricLine>,
    val removedCount: Int,
    val writers: List<String>
)

/**
 * Pure, non-destructive filter for removing non-sung info/credit/copyright lines
 * from online lyrics and extracting songwriter credits (`Written by: ...`).
 */
internal object OnlineLyricInfoFilter {

    fun process(
        lines: List<OnlineLyricFetcher.LyricLine>,
        rawLyrics: String? = null,
        context: TrackFilterContext = TrackFilterContext(),
        filterInfoLines: Boolean = true
    ): InfoFilterResult {
        val ttmlSongwriters = extractTtmlSongwriters(rawLyrics)
        val yrcCredits = extractYrcJsonCredits(rawLyrics)

        if (lines.isEmpty()) {
            val writers = resolveWriters(ttmlSongwriters, yrcCredits, LyricCredits())
            return InfoFilterResult(lines = lines, removedCount = 0, writers = writers)
        }

        val infoFlags = detectInfoLines(lines, context)
        val lineCredits = extractCreditsFromLines(
            lines.filterIndexed { index, _ -> infoFlags[index] },
            context
        )
        val writers = resolveWriters(ttmlSongwriters, yrcCredits, lineCredits)

        if (!filterInfoLines) {
            return InfoFilterResult(lines = lines, removedCount = 0, writers = writers)
        }

        val filtered = lines.filterIndexed { index, _ -> !infoFlags[index] }
        // Fail-safe: never empty out an entire song (e.g. single-line instrumental notice).
        if (filtered.isEmpty()) {
            return InfoFilterResult(lines = lines, removedCount = 0, writers = writers)
        }

        return InfoFilterResult(
            lines = filtered,
            removedCount = lines.size - filtered.size,
            writers = writers
        )
    }

    /**
     * Checks whether a sidecar (translation / romanization) line is a platform copyright
     * banner or QQ placeholder (`//`) that must not attach to a real lyric line.
     */
    fun isSidecarNoiseLine(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isEmpty() || trimmed == "//" || trimmed == "/") return true
        val normalized = normalizeForMatch(trimmed)
        return isPlatformClaimOrCopyright(normalized)
    }

    internal fun detectInfoLines(
        lines: List<OnlineLyricFetcher.LyricLine>,
        context: TrackFilterContext = TrackFilterContext()
    ): BooleanArray {
        val n = lines.size
        val flags = BooleanArray(n)
        if (n == 0) return flags

        val artistTokens = collectArtistTokens(context)
        val titleCandidates = collectTitleCandidates(context)
        val hasDuetMarkers = hasDuetSpeakerLines(lines, artistTokens)

        // 1. Scan head zone
        val repeatingColonLabels = collectRepeatingColonLabels(lines)
        val headCount = scanHeadInfoCount(lines, titleCandidates, artistTokens, hasDuetMarkers, repeatingColonLabels)
        for (i in 0 until headCount.coerceAtMost(n)) {
            flags[i] = true
        }

        // 2. Scan tail zone (disjoint from head)
        val tailCount = scanTailInfoCount(lines, headCount, artistTokens, hasDuetMarkers, repeatingColonLabels)
        val tailStart = (n - tailCount).coerceAtLeast(headCount)
        for (i in tailStart until n) {
            flags[i] = true
        }

        // 3. Middle zone: ultra-conservative (high-confidence copyright/watermark banners only)
        for (i in headCount until tailStart) {
            val normalized = normalizeForMatch(lines[i].text)
            if (isPlatformClaimOrCopyright(normalized)) {
                flags[i] = true
            }
        }

        return flags
    }

    private fun scanHeadInfoCount(
        lines: List<OnlineLyricFetcher.LyricLine>,
        titleCandidates: List<String>,
        artistTokens: Set<String>,
        hasDuetMarkers: Boolean,
        repeatingColonLabels: Set<String>
    ): Int {
        val n = lines.size
        if (n == 0) return 0

        var index = 0
        val firstText = lines[0].text.trim()
        val firstMatchedHeader = when {
            looksLikeTitleAndArtistHeader(firstText, titleCandidates, artistTokens) -> true
            looksLikeTitleOnlyHeader(firstText, titleCandidates) &&
                n >= 2 && isZoneCreditOrClaimLine(lines[1].text, artistTokens, hasDuetMarkers, allowWeakSingleCharNoColon = false) -> true
            looksLikeStructuralTitleArtistHeader(firstText, lines[0].startTime) &&
                n >= 2 && isZoneCreditOrClaimLine(lines[1].text, artistTokens, hasDuetMarkers, allowWeakSingleCharNoColon = false) -> true
            else -> false
        }
        if (firstMatchedHeader) {
            index = 1
        }

        var sawConfirmedCredit = false
        while (index < n) {
            val text = lines[index].text.trim()
            if (isIgnorableSpacer(text)) {
                if (hasUpcomingZoneInfo(lines, index, artistTokens, hasDuetMarkers, sawConfirmedCredit)) {
                    index++
                    continue
                }
                break
            }

            if (isZoneCreditOrClaimLine(text, artistTokens, hasDuetMarkers, allowWeakSingleCharNoColon = sawConfirmedCredit)) {
                sawConfirmedCredit = true
                index++
                continue
            }

            // Unknown `<short label>：<value>` inside or trailing a confirmed head credit block,
            // or at line 0/1 right before confirmed credits.
            if (looksLikeGenericCreditKeyValue(text, artistTokens, repeatingColonLabels)) {
                val bridged = (sawConfirmedCredit || index <= 1) &&
                    hasUpcomingZoneInfo(lines, index, artistTokens, hasDuetMarkers, sawConfirmedCredit = true)
                val closelyFollowingCredit = sawConfirmedCredit &&
                    index > 0 &&
                    (lines[index].startTime - lines[index - 1].startTime) in 0L..MAX_ADJACENT_CREDIT_GAP_MS
                if (bridged || closelyFollowingCredit) {
                    index++
                    continue
                }
            }

            break
        }
        return index
    }

    private fun scanTailInfoCount(
        lines: List<OnlineLyricFetcher.LyricLine>,
        headCount: Int,
        artistTokens: Set<String>,
        hasDuetMarkers: Boolean,
        repeatingColonLabels: Set<String>
    ): Int {
        var count = 0
        var sawConfirmedCredit = false
        var index = lines.lastIndex
        while (index >= headCount) {
            val text = lines[index].text.trim()
            if (isIgnorableSpacer(text)) {
                if (hasPreviousZoneInfo(lines, index, headCount, artistTokens, hasDuetMarkers, sawConfirmedCredit)) {
                    count++
                    index--
                    continue
                }
                break
            }

            if (isZoneCreditOrClaimLine(text, artistTokens, hasDuetMarkers, allowWeakSingleCharNoColon = sawConfirmedCredit)) {
                sawConfirmedCredit = true
                count++
                index--
                continue
            }

            if (looksLikeGenericCreditKeyValue(text, artistTokens, repeatingColonLabels)) {
                val bridged = (sawConfirmedCredit || index >= lines.lastIndex - 1) &&
                    hasPreviousZoneInfo(lines, index, headCount, artistTokens, hasDuetMarkers, sawConfirmedCredit = true)
                val closelyPrecedingCredit = sawConfirmedCredit &&
                    index < lines.lastIndex &&
                    (lines[index + 1].startTime - lines[index].startTime) in 0L..MAX_ADJACENT_CREDIT_GAP_MS
                if (bridged || closelyPrecedingCredit) {
                    count++
                    index--
                    continue
                }
            }

            break
        }
        return count
    }

    private fun hasUpcomingZoneInfo(
        lines: List<OnlineLyricFetcher.LyricLine>,
        currentIndex: Int,
        artistTokens: Set<String>,
        hasDuetMarkers: Boolean,
        sawConfirmedCredit: Boolean
    ): Boolean {
        val maxLookahead = if (sawConfirmedCredit) 3 else 2
        var checked = 0
        for (i in (currentIndex + 1) until lines.size) {
            val text = lines[i].text.trim()
            if (isIgnorableSpacer(text)) continue
            checked++
            if (isZoneCreditOrClaimLine(text, artistTokens, hasDuetMarkers, allowWeakSingleCharNoColon = sawConfirmedCredit)) {
                return true
            }
            if (checked >= maxLookahead) break
        }
        return false
    }

    private fun hasPreviousZoneInfo(
        lines: List<OnlineLyricFetcher.LyricLine>,
        currentIndex: Int,
        headCount: Int,
        artistTokens: Set<String>,
        hasDuetMarkers: Boolean,
        sawConfirmedCredit: Boolean
    ): Boolean {
        val maxLookahead = if (sawConfirmedCredit) 3 else 2
        var checked = 0
        for (i in (currentIndex - 1) downTo headCount) {
            val text = lines[i].text.trim()
            if (isIgnorableSpacer(text)) continue
            checked++
            if (isZoneCreditOrClaimLine(text, artistTokens, hasDuetMarkers, allowWeakSingleCharNoColon = sawConfirmedCredit)) {
                return true
            }
            if (checked >= maxLookahead) break
        }
        return false
    }

    private fun isIgnorableSpacer(text: String): Boolean {
        return text.isBlank() || text == "//" || text == "-" || text == "—" || text == "···"
    }

    /**
     * Evaluates whether a line in the head or tail zone is a credit/staff/copyright line.
     */
    internal fun isZoneCreditOrClaimLine(
        rawText: String,
        artistTokens: Set<String>,
        hasDuetMarkers: Boolean,
        allowWeakSingleCharNoColon: Boolean
    ): Boolean {
        val trimmed = rawText.trim()
        if (trimmed.isEmpty()) return false

        val normalized = normalizeForMatch(trimmed)
        if (isPlatformClaimOrCopyright(normalized)) return true

        // Colon-separated `<label>:<value>` check (Tier 2)
        val colonIndex = findCreditColonIndex(trimmed)
        if (colonIndex > 0) {
            val rawLabel = trimmed.substring(0, colonIndex).trim()
            val labelNorm = normalizeForMatch(rawLabel)
            if (isProtectedSpeakerLabel(labelNorm, artistTokens, hasDuetMarkers)) {
                return false
            }
            if (isCreditLabel(labelNorm, hasDuetMarkers)) {
                return true
            }
        }

        // Space-separated `<role> <name>` check without colon (Tier 3)
        if (isNoColonCreditLine(normalized, allowWeakSingleCharNoColon)) {
            return true
        }

        return false
    }

    private fun collectRepeatingColonLabels(lines: List<OnlineLyricFetcher.LyricLine>): Set<String> {
        val counts = HashMap<String, Int>()
        for (line in lines) {
            val trimmed = line.text.trim()
            val colonIndex = findCreditColonIndex(trimmed)
            if (colonIndex <= 0 || colonIndex > 20) continue
            val labelNorm = normalizeForMatch(trimmed.substring(0, colonIndex))
                .removePrefix("(").removeSuffix(")")
                .removePrefix("[").removeSuffix("]")
                .removePrefix("【").removeSuffix("】")
                .trim()
            if (labelNorm.isNotEmpty()) {
                counts[labelNorm] = (counts[labelNorm] ?: 0) + 1
            }
        }
        return counts.filterValues { it >= 2 }.keys
    }

    private fun looksLikeGenericCreditKeyValue(
        rawText: String,
        artistTokens: Set<String>,
        repeatingColonLabels: Set<String>
    ): Boolean {
        val trimmed = rawText.trim()
        val colonIndex = findCreditColonIndex(trimmed)
        if (colonIndex <= 0) return false
        val label = trimmed.substring(0, colonIndex).trim()
        val value = trimmed.substring(colonIndex + 1).trim()
        if (label.isEmpty() || label.length > 36 || value.isEmpty()) return false
        val labelNorm = normalizeForMatch(label)
        if (isProtectedSpeakerLabel(labelNorm, artistTokens, hasDuetMarkers = true)) return false
        // A label that repeats multiple times across the song is far more likely a duet speaker than a one-off credit.
        if (labelNorm in repeatingColonLabels && !isCreditLabel(labelNorm, hasDuetMarkers = true)) return false
        if (NARRATIVE_PREFIX_REGEX.containsMatchIn(labelNorm)) return false
        if (NARRATIVE_SPEECH_SUFFIX_REGEX.containsMatchIn(labelNorm)) return false
        return true
    }

    /**
     * Finds the first `:`, `：`, or `∶` that is NOT part of a clock/time token like `12:30`.
     */
    internal fun findCreditColonIndex(text: String): Int {
        for (i in text.indices) {
            val ch = text[i]
            if (ch == ':' || ch == '：' || ch == '∶') {
                val prevIsDigit = i > 0 && text[i - 1].isDigit()
                val nextIsDigit = i + 1 < text.length && text[i + 1].isDigit()
                if (prevIsDigit && nextIsDigit) continue
                return i
            }
        }
        return -1
    }

    private fun isProtectedSpeakerLabel(
        labelNorm: String,
        artistTokens: Set<String>,
        hasDuetMarkers: Boolean
    ): Boolean {
        val stripped = labelNorm
            .removePrefix("(").removeSuffix(")")
            .removePrefix("[").removeSuffix("]")
            .removePrefix("【").removeSuffix("】")
            .trim()
        if (stripped.isEmpty()) return false
        if (stripped in PROTECTED_DUET_SPEAKERS) return true
        if (hasDuetMarkers && stripped in CONDITIONAL_DUET_SPEAKERS) return true
        if (stripped in artistTokens) return true
        return false
    }

    internal fun isCreditLabel(labelNorm: String, hasDuetMarkers: Boolean): Boolean {
        if (labelNorm.isEmpty() || labelNorm.length > MAX_CREDIT_LABEL_LENGTH) return false
        if (NARRATIVE_PREFIX_REGEX.containsMatchIn(labelNorm)) return false

        // Split composite labels such as "编曲 & 键盘 music arrangement & keyboards" or "词/曲"
        val subLabels = labelNorm.split(LABEL_PART_SPLIT_REGEX)
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        for (part in subLabels) {
            val noParen = part.replace(PAREN_CONTENT_REGEX, "").trim()
            // Exact single-character Chinese role token (or glued directly to ASCII English like `词lyricist`)
            if (part in SINGLE_CHAR_CN_ROLES ||
                noParen in SINGLE_CHAR_CN_ROLES ||
                SINGLE_CHAR_CN_GLUED_ASCII_REGEX.containsMatchIn(noParen)
            ) {
                return true
            }
            // Multi-char Chinese/Japanese/Korean role keywords inside label part
            if (MULTI_CHAR_CJK_ROLES.any { keyword -> part.contains(keyword) }) {
                if (hasDuetMarkers && noParen == "合唱") continue
                return true
            }
            // English/abbreviation role keywords matched on ASCII-letter boundaries
            // (must NOT use `\b` because Android ART ICU treats CJK chars as `\w`, breaking `词Lyricist`)
            if (ENGLISH_ROLE_REGEX.containsMatchIn(part)) return true
            if (ENGLISH_BY_ROLE_REGEX.containsMatchIn(part)) return true
        }
        return false
    }

    private fun isNoColonCreditLine(
        normalizedLine: String,
        allowWeakSingleChar: Boolean
    ): Boolean {
        if (STRONG_NO_COLON_CN_REGEX.containsMatchIn(normalizedLine)) return true
        if (STRONG_NO_COLON_EN_REGEX.containsMatchIn(normalizedLine)) return true
        if (allowWeakSingleChar && WEAK_SINGLE_CHAR_NO_COLON_REGEX.containsMatchIn(normalizedLine)) return true
        return false
    }

    private fun isPlatformClaimOrCopyright(normalizedLine: String): Boolean {
        if (normalizedLine.isEmpty()) return false

        // Tencent / TME translation copyright claiming
        if ((normalizedLine.contains("腾讯") || normalizedLine.contains("tme")) &&
            normalizedLine.contains("享有") &&
            normalizedLine.contains("翻译") &&
            normalizedLine.contains("权")
        ) {
            return true
        }

        // Unauthorized-use legal disclaimer (>= 4 hits)
        var disclaimerScore = 0
        for (keyword in COPYRIGHT_CLAIM_KEYWORDS) {
            if (normalizedLine.contains(keyword)) disclaimerScore++
        }
        if (disclaimerScore >= 4) return true

        // Contributor / platform watermarks
        if (PLATFORM_WATERMARK_REGEX.containsMatchIn(normalizedLine)) return true

        return false
    }

    private fun looksLikeTitleAndArtistHeader(
        line: String,
        titleCandidates: List<String>,
        artistTokens: Set<String>
    ): Boolean {
        if (line.isBlank() || line.length > 140) return false
        if (titleCandidates.isEmpty() || artistTokens.isEmpty()) return false
        val normLine = normalizeForMatch(line)
        val hasTitle = titleCandidates.any { candidate -> matchesTitleCandidate(normLine, candidate) }
        if (!hasTitle) return false
        val hasArtist = artistTokens.any { token -> token.length >= 2 && normLine.contains(token) } ||
            artistTokens.any { token -> token.length == 1 && (normLine.endsWith("-$token") || normLine.endsWith("- $token")) }
        return hasArtist
    }

    private fun looksLikeTitleOnlyHeader(line: String, titleCandidates: List<String>): Boolean {
        if (line.isBlank() || titleCandidates.isEmpty()) return false
        val normLine = normalizeForMatch(line)
        return titleCandidates.any { candidate -> normLine == candidate }
    }

    private fun looksLikeStructuralTitleArtistHeader(line: String, startTimeMs: Long): Boolean {
        if (line.isBlank() || line.length > 90) return false
        if (startTimeMs > 10_000L) return false
        if (SENTENCE_PUNCT_REGEX.containsMatchIn(line)) return false
        val sepMatch = listOf(" - ", " — ", " – ", "-", "—", "–").firstNotNullOfOrNull { sep ->
            val idx = line.indexOf(sep)
            if (idx > 0 && idx + sep.length < line.length) idx to sep.length else null
        } ?: return false
        val left = line.substring(0, sepMatch.first).trim()
        val right = line.substring(sepMatch.first + sepMatch.second).trim()
        return left.isNotEmpty() && right.isNotEmpty()
    }

    private fun matchesTitleCandidate(normLine: String, candidate: String): Boolean {
        if (candidate.isBlank()) return false
        if (normLine.contains(candidate)) return true
        val beforeParen = candidate.substringBefore('(').trim()
        if (beforeParen.length >= 2 && normLine.contains(beforeParen)) return true
        val beforeDash = candidate.substringBefore(" - ").trim()
        if (beforeDash.length >= 2 && normLine.contains(beforeDash)) return true
        return false
    }

    private fun collectTitleCandidates(context: TrackFilterContext): List<String> {
        return listOfNotNull(context.title, context.rawTitle, context.matchedTitle)
            .map { normalizeForMatch(it) }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun collectArtistTokens(context: TrackFilterContext): Set<String> {
        return listOfNotNull(context.artist, context.rawArtist, context.matchedArtist)
            .flatMap { raw ->
                val norm = normalizeForMatch(raw)
                val full = listOf(norm)
                val split = norm.split(ARTIST_SPLIT_REGEX)
                val parenAliases = PAREN_CONTENT_REGEX.findAll(norm).map { it.groupValues[1] }.toList()
                val strippedParen = norm.replace(PAREN_CONTENT_REGEX, " ")
                    .split(ARTIST_SPLIT_REGEX)
                full + split + parenAliases + strippedParen
            }
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toSet()
    }

    private fun hasDuetSpeakerLines(
        lines: List<OnlineLyricFetcher.LyricLine>,
        artistTokens: Set<String>
    ): Boolean {
        val speakers = mutableSetOf<String>()
        for (line in lines) {
            val trimmed = line.text.trim()
            val candidateLabel = extractLeadingSpeakerCandidate(trimmed) ?: continue
            if (candidateLabel in SOLO_DUET_SPEAKERS || candidateLabel in artistTokens) {
                speakers.add(candidateLabel)
                if (speakers.size >= 2) return true
            }
        }
        return false
    }

    private fun extractLeadingSpeakerCandidate(trimmed: String): String? {
        val colon = findCreditColonIndex(trimmed)
        if (colon > 0 && colon <= 24) {
            return normalizeForMatch(trimmed.substring(0, colon))
                .removePrefix("(").removeSuffix(")")
                .removePrefix("[").removeSuffix("]")
                .removePrefix("【").removeSuffix("】")
                .trim()
                .takeIf { it.isNotEmpty() }
        }
        val bracketMatch = LEADING_BRACKET_SPEAKER_REGEX.find(trimmed) ?: return null
        return normalizeForMatch(bracketMatch.groupValues[1]).takeIf { it.isNotEmpty() }
    }

    // ── Multi-source writer extraction ─────────────────────────────────────

    private fun resolveWriters(
        ttmlSongwriters: List<String>,
        yrcCredits: LyricCredits,
        lineCredits: LyricCredits
    ): List<String> {
        if (ttmlSongwriters.isNotEmpty()) {
            return LyricCredits.deduplicateNames(ttmlSongwriters)
        }
        val combined = LyricCredits(
            lyricists = yrcCredits.lyricists + lineCredits.lyricists,
            composers = yrcCredits.composers + lineCredits.composers,
            songwriters = yrcCredits.songwriters + lineCredits.songwriters
        )
        return combined.mergedWriters()
    }

    internal fun extractTtmlSongwriters(rawLyrics: String?): List<String> {
        if (rawLyrics.isNullOrBlank()) return emptyList()
        if (!rawLyrics.contains("songwriter", ignoreCase = true)) return emptyList()
        val writers = mutableListOf<String>()
        for (match in TTML_SONGWRITER_REGEX.findAll(rawLyrics)) {
            val decoded = decodeXmlEntities(match.groupValues[1]).trim()
            if (decoded.isNotBlank() && !isPlaceholderWriter(decoded)) {
                writers.add(decoded)
            }
        }
        return LyricCredits.deduplicateNames(writers)
    }

    internal fun extractYrcJsonCredits(rawLyrics: String?): LyricCredits {
        if (rawLyrics.isNullOrBlank() || !rawLyrics.contains('{')) return LyricCredits()
        val lyricists = mutableListOf<String>()
        val composers = mutableListOf<String>()
        val songwriters = mutableListOf<String>()

        for (rawLine in rawLyrics.lineSequence()) {
            val trimmed = rawLine.trim()
            if (!trimmed.startsWith("{") || !trimmed.endsWith("}")) continue
            val obj = runCatching { JSONObject(trimmed) }.getOrNull() ?: continue
            val creditsArray = obj.optJSONArray("c") ?: continue
            if (creditsArray.length() == 0) continue

            val parts = buildList {
                for (i in 0 until creditsArray.length()) {
                    val tx = creditsArray.optJSONObject(i)?.optString("tx").orEmpty()
                    if (tx.isNotEmpty()) add(tx)
                }
            }
            if (parts.isEmpty()) continue

            val joined = parts.joinToString("")
            collectCreditsFromSingleLine(joined, lyricists, composers, songwriters)
        }

        return LyricCredits(
            lyricists = LyricCredits.deduplicateNames(lyricists),
            composers = LyricCredits.deduplicateNames(composers),
            songwriters = LyricCredits.deduplicateNames(songwriters)
        )
    }

    private fun extractCreditsFromLines(
        removedLines: List<OnlineLyricFetcher.LyricLine>,
        @Suppress("UNUSED_PARAMETER") context: TrackFilterContext
    ): LyricCredits {
        if (removedLines.isEmpty()) return LyricCredits()
        val lyricists = mutableListOf<String>()
        val composers = mutableListOf<String>()
        val songwriters = mutableListOf<String>()

        for (line in removedLines) {
            collectCreditsFromSingleLine(line.text, lyricists, composers, songwriters)
        }

        return LyricCredits(
            lyricists = LyricCredits.deduplicateNames(lyricists),
            composers = LyricCredits.deduplicateNames(composers),
            songwriters = LyricCredits.deduplicateNames(songwriters)
        )
    }

    private fun collectCreditsFromSingleLine(
        rawLine: String,
        lyricists: MutableList<String>,
        composers: MutableList<String>,
        songwriters: MutableList<String>
    ) {
        val trimmed = rawLine.trim()
        if (trimmed.isEmpty()) return

        val colonIndex = findCreditColonIndex(trimmed)
        val labelRaw: String
        val valueRaw: String
        if (colonIndex > 0) {
            labelRaw = trimmed.substring(0, colonIndex).trim()
            valueRaw = trimmed.substring(colonIndex + 1).trim()
        } else {
            val spaceMatch = NO_COLON_WRITER_EXTRACT_REGEX.find(trimmed) ?: return
            labelRaw = spaceMatch.groupValues[1].trim()
            valueRaw = spaceMatch.groupValues[2].trim()
        }
        if (labelRaw.isEmpty() || valueRaw.isEmpty()) return

        val labelNorm = normalizeForMatch(labelRaw)
        val role = classifyWriterRole(labelNorm) ?: return
        val names = splitWriterNames(valueRaw)
        if (names.isEmpty()) return

        when (role) {
            WriterRole.LYRICIST -> lyricists.addAll(names)
            WriterRole.COMPOSER -> composers.addAll(names)
            WriterRole.BOTH -> songwriters.addAll(names)
        }
    }

    private enum class WriterRole { LYRICIST, COMPOSER, BOTH }

    private fun classifyWriterRole(labelNorm: String): WriterRole? {
        if (labelNorm.length > MAX_CREDIT_LABEL_LENGTH) return null

        val parts = labelNorm.split(LABEL_PART_SPLIT_REGEX)
            .map { it.trim() }
            .filter { it.isNotEmpty() }

        var isLyricist = false
        var isComposer = false
        var isCombined = false

        for (part in parts) {
            // Exclude non-writing roles on this sub-label (e.g. 编曲, 曲绘, 歌词贡献者, 配唱制作, 作曲助理)
            if (NON_WRITER_EXCLUSIONS.any { part.contains(it) }) continue
            val noParen = part.replace(PAREN_CONTENT_REGEX, "").trim()
            when {
                part in COMBINED_WRITER_LABELS ||
                    noParen in COMBINED_WRITER_LABELS ||
                    noParen.endsWith("词曲") ||
                    COMBINED_WRITER_EN_REGEX.containsMatchIn(part) -> isCombined = true
                part in LYRICIST_LABELS ||
                    noParen in LYRICIST_LABELS ||
                    noParen.endsWith("作词") ||
                    noParen.endsWith("填词") ||
                    LYRICIST_EN_REGEX.containsMatchIn(part) -> isLyricist = true
                part in COMPOSER_LABELS ||
                    noParen in COMPOSER_LABELS ||
                    noParen.endsWith("作曲") ||
                    noParen.endsWith("谱曲") ||
                    COMPOSER_EN_REGEX.containsMatchIn(part) -> isComposer = true
            }
        }

        return when {
            isCombined || (isLyricist && isComposer) -> WriterRole.BOTH
            isLyricist -> WriterRole.LYRICIST
            isComposer -> WriterRole.COMPOSER
            else -> null
        }
    }

    internal fun splitWriterNames(rawValue: String): List<String> {
        val cleaned = rawValue
            .trim()
            .trimStart(':', '：', '-', '—', '/')
            .trimEnd('/', '、', ',', '，', ';', '；')
            .trim()
        if (cleaned.isEmpty()) return emptyList()

        return cleaned.split(WRITER_NAME_SPLIT_REGEX)
            .map { it.trim().replace(Regex("\\s+"), " ") }
            .filter { it.isNotEmpty() && !isPlaceholderWriter(it) }
    }

    private fun isPlaceholderWriter(name: String): Boolean {
        val norm = normalizeForMatch(name)
        return norm.isEmpty() || norm in PLACEHOLDER_WRITERS
    }

    internal fun normalizeForMatch(text: String): String {
        val simplified = LyricChineseNormalizer.toSimplified(text)
        return simplified
            .replace("\uFEFF", "")
            .replace("\u200B", "")
            .replace("\u200C", "")
            .replace("\u200D", "")
            .replace('：', ':')
            .replace('∶', ':')
            .replace('／', '/')
            .replace('（', '(')
            .replace('）', ')')
            .replace('’', '\'')
            .replace('‘', '\'')
            .replace('\u3000', ' ')
            .lowercase(Locale.ROOT)
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun decodeXmlEntities(text: String): String {
        return text
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")
    }

    private const val MAX_CREDIT_LABEL_LENGTH = 64
    private const val MAX_ADJACENT_CREDIT_GAP_MS = 12_000L

    private val LABEL_PART_SPLIT_REGEX = Regex("""[/&、+,·｜|]+""")
    private val ARTIST_SPLIT_REGEX = Regex("""(?:\s*(?:/|&|,|、|;|×|feat\.?|ft\.?)\s*)""", RegexOption.IGNORE_CASE)
    private val PAREN_CONTENT_REGEX = Regex("""\(([^)]+)\)""")
    private val LEADING_BRACKET_SPEAKER_REGEX = Regex("""^[(（\[【]([^)）\]】]{1,16})[)）\]】]""")
    private val SENTENCE_PUNCT_REGEX = Regex("""[。？！?!，,；;]""")
    private val WRITER_NAME_SPLIT_REGEX = Regex("""\s*[/、,，;；&＆]\s*""")
    private val TTML_SONGWRITER_REGEX = Regex("""<songwriter\b[^>]*>(.*?)</songwriter>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))

    private val NARRATIVE_PREFIX_REGEX = Regex("""^(?:我|你|他|她|它|我们|你们|他们|她们|谁|大家|有人|她说|我说|你说|他说|她问|我问)""")
    private val NARRATIVE_SPEECH_SUFFIX_REGEX = Regex("""(?:说|问|答|道|喊|叫|唱|写|讲|叹|想|着|了|的|吗|呢|吧|啊|呀|says|said|asked|replied)$""", RegexOption.IGNORE_CASE)

    private val SOLO_DUET_SPEAKERS = setOf("男", "女", "male", "female")
    private val PROTECTED_DUET_SPEAKERS = setOf("男", "女", "合", "齐", "男女", "男女合", "男/女", "male", "female", "both", "all", "duet")
    private val CONDITIONAL_DUET_SPEAKERS = setOf("合唱", "童", "童声", "独白", "chorus")

    private val SINGLE_CHAR_CN_ROLES = setOf("词", "曲", "编", "唱", "混", "录", "鼓", "笛", "箫")
    private val SINGLE_CHAR_CN_GLUED_ASCII_REGEX = Regex("""^(?:词|曲|编|唱|混|录|鼓|笛|箫)\s*[a-z]""")

    private val MULTI_CHAR_CJK_ROLES = listOf(
        "作词", "填词", "词曲", "中文词", "原词", "歌词", "作词人",
        "作曲", "谱曲", "原曲", "作曲人",
        "编曲", "编配", "配器",
        "制作", "制作人", "配唱", "监制", "出品", "发行", "企划", "策划",
        "统筹", "总监", "宣传", "宣发", "营销", "行销", "推广", "运营",
        "文案", "编辑", "校对", "翻译", "鸣谢", "致谢", "感谢",
        "原唱", "演唱", "主唱", "伴唱", "和声", "和音", "合声", "合音", "人声", "声乐", "童声", "戏腔", "念白", "合唱",
        "录音", "混音", "缩混", "录混", "贴混", "贴唱", "母带", "分轨", "工程", "调音", "修音", "后期", "音频", "声音",
        "吉他", "吉它", "箱琴", "贝斯", "键盘", "钢琴", "弦乐", "管弦", "管乐", "打击乐", "打击", "合成器",
        "提琴", "小号", "长号", "圆号", "萨克斯", "单簧管", "簧管", "巴松", "笛子", "长笛", "短笛", "竹笛", "排箫",
        "口琴", "手风琴", "爵士鼓", "架子鼓", "定音鼓",
        "古筝", "古琴", "琵琶", "二胡", "胡琴", "扬琴", "唢呐", "尺八", "马头琴", "葫芦丝", "热瓦普", "冬不拉", "科布兹", "巴拉莱卡",
        "乐队", "指挥", "助理", "顾问", "经纪", "艺人", "歌手", "歌名", "曲名", "歌曲", "专辑", "版权", "唱片",
        "厂牌", "工作室", "音乐室", "音频室", "团队", "协作", "协力", "公司", "承制",
        "商务", "合作", "媒介", "管理", "单位", "支持", "平台", "执行",
        "封面", "海报", "视觉", "设计", "摄影", "影像", "导演", "造型", "妆发", "拍摄", "服装", "制片", "灯光", "道具", "剪辑", "调色", "合成",
        "题字", "曲绘", "图绘", "调校", "调教", "伴奏", "专辑介绍",
        "歌词贡献", "翻译贡献", "时间轴", "lrc制作", "译词",
        // Japanese / Korean
        "作詞", "作曲", "編曲", "訳詞", "演奏", "録音",
        "작사", "작곡", "편곡", "프로듀서", "노래"
    )

    // Note: Use ASCII-letter lookaround `(?<![a-z])...(?![a-z])` instead of `\b`.
    // Android ART's ICU regex engine treats CJK ideographs as `\w`, which makes `\b`
    // fail on CJK+English glued labels like `词Lyricist` or `曲Composer`.
    internal val ENGLISH_ROLE_REGEX = Regex(
        """(?<![a-z])(?:op|sp|oa|ot|pv|a&r|pgm|isrc|bpm|midi|rec|arr|prod|lyrics?|lyricist|composer|arranger|arrangement|producer|production|vocals?|vocalist|backing|background|chorus|choir|harmony|hormony|recording|record|mixing|mixer|mix|mastering|master|engineer|engineering|editing|audio|sound|program|programmer|programming|programing|synth|synthesizer|keyboards?|piano|guitars?|bass|drums?|percussion|beats?|strings?|violins?|viola|cello|harp|organ|accordion|brass|horns?|trumpet|trombone|winds?|flute|clarinet|oboe|bassoon|saxophone|harmonica|ocarina|whistle|kobza|balalaika|conductor|studio|assistant|assist|publisher|publishing|distributor|label|copyright|album|singer|artist|artwork|design|cover|photography|stylist|director|supervisor|coordinator|marketing|promotion|writer|writers|songwriter|songwriters)(?![a-z])""",
        RegexOption.IGNORE_CASE
    )

    internal val ENGLISH_BY_ROLE_REGEX = Regex(
        """(?<![a-z])(?:written|lyrics|words|music|composed|arranged|produced|mixed|mastered|recorded|engineered|edited|performed|sung|directed|conducted|programmed)\s+by(?![a-z])""",
        RegexOption.IGNORE_CASE
    )

    private val STRONG_NO_COLON_CN_REGEX = Regex(
        """^(?:作词人|作词|填词|词曲|作曲人|作曲|谱曲|编曲|制作人|制作|配唱制作|监制|混音|缩混|母带|录音|和声|和音|合声|吉他|贝斯|键盘|钢琴|弦乐|原唱|演唱|出品|发行|企划|策划|统筹|文案|封面|鸣谢|版权|歌词贡献者|翻译贡献者|op|sp)\s+\S+"""
    )

    private val STRONG_NO_COLON_EN_REGEX = Regex(
        """^(?:written|lyrics|words|music|composed|arranged|produced|mixed|mastered|recorded)\s+by\s+\S+""",
        RegexOption.IGNORE_CASE
    )

    private val WEAK_SINGLE_CHAR_NO_COLON_REGEX = Regex("""^(?:词|曲|编|唱|混|录)\s+\S+""")

    private val NO_COLON_WRITER_EXTRACT_REGEX = Regex(
        """^(作词人|作词|填词|词曲|作曲人|作曲|谱曲|词|曲|written\s+by|lyrics\s+by|words\s+by|composed\s+by|music\s+by)\s+(.+)$""",
        RegexOption.IGNORE_CASE
    )

    private val COPYRIGHT_CLAIM_KEYWORDS = listOf(
        "未经", "许可", "授权", "不得", "请勿", "禁止", "翻唱", "翻录", "使用", "著作权", "版权"
    )

    private val PLATFORM_WATERMARK_REGEX = Regex(
        """(?:^本歌词由.+提供|^歌词贡献者\s*:|^翻译贡献者\s*:|^lrc(?:歌词)?(?:制作|编辑)\s*:|^qq音乐$|^网易云音乐$|^酷狗音乐$)"""
    )

    private val NON_WRITER_EXCLUSIONS = listOf(
        "编曲", "曲绘", "歌词贡献", "翻译", "配唱", "和声", "和音", "合声", "合音",
        "混音", "母带", "录音", "后期", "版权", "工作室", "音乐室",
        "助理", "统筹", "监制", "企划", "制作",
        "arrangement", "arranged", "arranger", "production", "producer", "vocal",
        "backing", "background", "assistant", "studio", "engineer", "copyright", "supervisor", "director"
    )

    private val COMBINED_WRITER_LABELS = setOf(
        "词曲", "作词作曲", "词曲作者", "词曲创作", "词曲编", "作词/作曲", "词/曲"
    )
    private val LYRICIST_LABELS = setOf(
        "词", "作词", "填词", "中文词", "原词", "歌词", "作词人", "译词", "作詞", "訳詞", "작사"
    )
    private val COMPOSER_LABELS = setOf(
        "曲", "作曲", "谱曲", "原曲", "作曲人", "작곡"
    )
    internal val COMBINED_WRITER_EN_REGEX = Regex(
        """(?<![a-z])(?:written\s+by|songwriters?|writers?|words\s*(?:&|and)\s*music|lyrics\s*(?:&|and)\s*music)(?![a-z])""",
        RegexOption.IGNORE_CASE
    )
    internal val LYRICIST_EN_REGEX = Regex(
        """(?<![a-z])(?:lyrics?(?:\s+by)?|lyricist|words(?:\s+by)?|text(?:\s+by)?)(?![a-z])""",
        RegexOption.IGNORE_CASE
    )
    internal val COMPOSER_EN_REGEX = Regex(
        """(?<![a-z])(?:composed\s+by|composer|music(?:\s+by)?|melody(?:\s+by)?)(?![a-z])""",
        RegexOption.IGNORE_CASE
    )

    private val PLACEHOLDER_WRITERS = setOf(
        "无", "暂无", "未知", "佚名", "none", "n/a", "unknown", "-", "--", "—", "/"
    )
}
