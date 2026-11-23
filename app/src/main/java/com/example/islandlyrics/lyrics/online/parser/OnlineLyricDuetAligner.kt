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

// Apple Music TTML agent-alignment semantics inspired by Lyricify-Lyrics-Helper (TtmlParser.cs)
// (https://github.com/WXRIW/Lyricify-Lyrics-Helper)
// Copyright (C) WXRIW/Lyricify-Lyrics-Helper contributors
// Licensed under Apache-2.0

package com.example.islandlyrics.lyrics.online.parser

import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher.LyricAlignment
import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher.LyricLine
import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher.SyllableInfo
import kotlin.math.abs

/**
 * Pure, non-destructive duet / alternating-vocal alignment processor.
 *
 * Supports two detection channels:
 * 1. Apple Music TTML native `ttm:agent` metadata (mirrors Apple Music's stateful agent alignment).
 * 2. Text speaker prefixes (`男：`/`女：`/`合：`, bracket variants, and named artist prefixes)
 *    with strict ≥ 2 distinct solo-vocalist activation, sticky continuation, optional prefix
 *    stripping, and exact syllable offset trimming.
 */
internal object OnlineLyricDuetAligner {

    fun process(
        lines: List<LyricLine>,
        rawLyrics: String? = null,
        context: TrackFilterContext = TrackFilterContext(),
        enabled: Boolean = true,
        stripPrefixes: Boolean = true
    ): List<LyricLine> {
        if (!enabled || lines.isEmpty()) return lines

        // Channel A: Apple Music TTML native `ttm:agent`
        val ttmlAligned = tryApplyTtmlAgentAlignment(lines, rawLyrics)
        if (ttmlAligned != null) {
            return ttmlAligned
        }

        // Channel B: Text speaker prefixes (`男:`/`女:`/`合:` + named artist labels)
        return applyTextSpeakerAlignment(lines, context, stripPrefixes)
    }

    // ── Channel A: Apple Music TTML `ttm:agent` ────────────────────────────

    private data class TtmlAgent(val id: String, val type: String)

    private data class TtmlParagraphAlignment(
        val startTimeMs: Long,
        val normalizedText: String,
        val alignment: LyricAlignment
    )

    private class TtmlAgentAlignmentState(agents: List<TtmlAgent>) {
        private val agentMap: Map<String, TtmlAgent> = agents.associateBy { it.id }
        private val hasAgentMetadata: Boolean = agentMap.isNotEmpty()
        private val isDuet: Boolean = agentMap.size > 1
        private var currentPersonId: String? = null
        private var isFlipped: Boolean = false

        fun getAlignment(agentId: String?): LyricAlignment {
            if (!hasAgentMetadata) return LyricAlignment.UNSPECIFIED
            if (!isDuet) return LyricAlignment.LEFT

            val trimmedId = agentId?.trim()
            val agent = if (!trimmedId.isNullOrEmpty()) agentMap[trimmedId] else null
            if (agent != null) {
                when {
                    agent.type.equals("person", ignoreCase = true) -> {
                        if (currentPersonId == null) {
                            currentPersonId = agent.id
                        } else if (currentPersonId != agent.id) {
                            isFlipped = !isFlipped
                            currentPersonId = agent.id
                        }
                    }
                    agent.type.equals("group", ignoreCase = true) -> return LyricAlignment.LEFT
                    agent.type.equals("other", ignoreCase = true) -> return LyricAlignment.RIGHT
                }
            }
            return if (isFlipped) LyricAlignment.RIGHT else LyricAlignment.LEFT
        }
    }

    private fun tryApplyTtmlAgentAlignment(
        lines: List<LyricLine>,
        rawLyrics: String?
    ): List<LyricLine>? {
        val trimmed = rawLyrics?.trimStart() ?: return null
        if (!trimmed.startsWith("<tt", ignoreCase = true)) return null

        val agents = parseTtmlAgents(trimmed)
        if (agents.size <= 1) return null

        val state = TtmlAgentAlignmentState(agents)
        val paragraphs = mutableListOf<TtmlParagraphAlignment>()

        for (match in TTML_P_REGEX.findAll(trimmed)) {
            val attrs = parseXmlAttributes(match.groupValues[1])
            val startMs = parseFlexibleTimeToMs(attrs["begin"]) ?: continue
            val bodyText = extractTtmlParagraphText(match.groupValues[2])
            if (bodyText.isBlank()) continue
            val agentId = attrs["ttm:agent"] ?: attrs["agent"]
            val alignment = state.getAlignment(agentId)
            paragraphs.add(
                TtmlParagraphAlignment(
                    startTimeMs = startMs,
                    normalizedText = normalizeTextForMatch(bodyText),
                    alignment = alignment
                )
            )
        }

        if (paragraphs.none { it.alignment == LyricAlignment.RIGHT }) return null
        paragraphs.sortBy { it.startTimeMs }

        val consumed = BooleanArray(paragraphs.size)
        var anyRightAssigned = false
        val aligned = lines.map { line ->
            val normLine = normalizeTextForMatch(line.text)
            var matchedIdx = -1
            var bestDelta = Long.MAX_VALUE

            // Pass 1: exact text + close startTime
            for (i in paragraphs.indices) {
                if (consumed[i]) continue
                val delta = abs(paragraphs[i].startTimeMs - line.startTime)
                if (delta <= TTML_TIME_TOLERANCE_MS && paragraphs[i].normalizedText == normLine && delta < bestDelta) {
                    bestDelta = delta
                    matchedIdx = i
                }
            }
            // Pass 2: fallback to closest unconsumed startTime
            if (matchedIdx < 0) {
                for (i in paragraphs.indices) {
                    if (consumed[i]) continue
                    val delta = abs(paragraphs[i].startTimeMs - line.startTime)
                    if (delta <= TTML_TIME_TOLERANCE_MS && delta < bestDelta) {
                        bestDelta = delta
                        matchedIdx = i
                    }
                }
            }

            if (matchedIdx >= 0) {
                consumed[matchedIdx] = true
                val side = paragraphs[matchedIdx].alignment
                if (side == LyricAlignment.RIGHT) anyRightAssigned = true
                line.copy(alignment = side)
            } else {
                line.copy(alignment = LyricAlignment.LEFT)
            }
        }

        return if (anyRightAssigned) aligned else null
    }

    private fun parseTtmlAgents(ttml: String): List<TtmlAgent> {
        val agents = mutableListOf<TtmlAgent>()
        val seenIds = HashSet<String>()
        for (match in TTML_AGENT_REGEX.findAll(ttml)) {
            val attrs = parseXmlAttributes(match.groupValues[1])
            val id = (attrs["xml:id"] ?: attrs["id"])?.trim().orEmpty()
            val type = attrs["type"]?.trim().orEmpty()
            if (id.isNotEmpty() && seenIds.add(id)) {
                agents.add(TtmlAgent(id = id, type = type))
            }
        }
        return agents
    }

    private fun extractTtmlParagraphText(body: String): String {
        val spanTexts = mutableListOf<String>()
        for (spanMatch in TTML_SPAN_REGEX.findAll(body)) {
            val raw = decodeXmlText(stripXmlTags(spanMatch.groupValues[2], separator = ""))
                .replace(MULTI_SPACE_REGEX, " ")
            if (raw.isNotEmpty()) spanTexts.add(raw)
        }
        return if (spanTexts.isNotEmpty()) {
            spanTexts.joinToString("").replace(MULTI_SPACE_REGEX, " ").trim()
        } else {
            decodeXmlText(stripXmlTags(body)).replace(MULTI_SPACE_REGEX, " ").trim()
        }
    }

    // ── Channel B: Text Speaker Prefixes ───────────────────────────────────

    private enum class SpeakerKind { SOLO, GROUP }

    private data class ParsedSpeakerPrefix(
        val kind: SpeakerKind,
        val canonicalId: String,
        val prefixEndIndex: Int
    )

    private data class ArtistMatcher(
        val canonicalTokens: Set<String>,
        val aliasToCanonical: Map<String, String>,
        val isMultiArtistTrack: Boolean
    )

    private fun applyTextSpeakerAlignment(
        lines: List<LyricLine>,
        context: TrackFilterContext,
        stripPrefixes: Boolean
    ): List<LyricLine> {
        val artistMatcher = buildArtistMatcher(context)
        val discoveredSolos = discoverUnlistedColonSolos(lines, artistMatcher)
        val parsedPerLine = lines.map { parseLineSpeakerPrefix(it.text, artistMatcher, discoveredSolos) }

        val distinctSolos = parsedPerLine
            .filterNotNull()
            .filter { it.kind == SpeakerKind.SOLO }
            .map { it.canonicalId }
            .toSet()

        // Strictly require >= 2 distinct solo vocalists so solo tracks never shift or strip prefixes.
        if (distinctSolos.size < 2) {
            return lines
        }

        var firstSoloId: String? = null
        var secondSoloId: String? = null
        var lastSoloId: String? = null
        var lastSoloAlignment = LyricAlignment.LEFT
        var currentAlignment = LyricAlignment.LEFT

        val result = ArrayList<LyricLine>(lines.size)
        for (i in lines.indices) {
            val line = lines[i]
            val parsed = parsedPerLine[i]

            if (parsed != null) {
                val side = when (parsed.kind) {
                    SpeakerKind.GROUP -> LyricAlignment.LEFT
                    SpeakerKind.SOLO -> {
                        val id = parsed.canonicalId
                        val resolved = when {
                            firstSoloId == null -> {
                                firstSoloId = id
                                LyricAlignment.LEFT
                            }
                            id == firstSoloId -> LyricAlignment.LEFT
                            secondSoloId == null -> {
                                secondSoloId = id
                                LyricAlignment.RIGHT
                            }
                            id == secondSoloId -> LyricAlignment.RIGHT
                            id != lastSoloId -> if (lastSoloAlignment == LyricAlignment.LEFT) {
                                LyricAlignment.RIGHT
                            } else {
                                LyricAlignment.LEFT
                            }
                            else -> lastSoloAlignment
                        }
                        lastSoloId = id
                        lastSoloAlignment = resolved
                        resolved
                    }
                }
                currentAlignment = side

                if (stripPrefixes) {
                    val newText = line.text.substring(parsed.prefixEndIndex)
                    // Standalone speaker cue line (e.g. "女：" with no sung lyrics):
                    // speaker state is now updated for subsequent lines; drop the empty cue line.
                    if (newText.isBlank()) {
                        continue
                    }
                    val trimmedSyllables = trimLeadingPrefixFromSyllables(
                        syllables = line.syllables,
                        originalText = line.text,
                        prefixLength = parsed.prefixEndIndex,
                        newText = newText
                    )
                    val trimmedTranslation = stripMatchingSidecarPrefix(line.translation, artistMatcher, discoveredSolos)
                    val trimmedRoma = stripMatchingSidecarPrefix(line.roma, artistMatcher, discoveredSolos)
                    result.add(
                        line.copy(
                            text = newText,
                            syllables = trimmedSyllables,
                            translation = trimmedTranslation,
                            roma = trimmedRoma,
                            alignment = currentAlignment
                        )
                    )
                } else {
                    result.add(line.copy(alignment = currentAlignment))
                }
            } else {
                // Sticky continuation: inherit current speaker's alignment
                result.add(line.copy(alignment = currentAlignment))
            }
        }

        // Safety guard: never return an empty song
        if (result.isEmpty()) return lines
        return result
    }

    /**
     * Discovers unlisted speaker labels using `:` / `：` prefixes in two high-precision modes:
     * 1. Standalone cue lines (e.g., `周杰伦：` / `阿信：` with no sung text after the colon).
     * 2. Inline colon speaker prefixes (e.g., `王俊凯：...` / `王源：...` / `阿信：...`) when either:
     *    - another speaker in the song was already confirmed via built-in tags, metadata artists,
     *      or standalone cue lines, OR
     *    - at least 2 distinct unlisted inline speaker labels appear across >= 3 tagged lines.
     *
     * Because ordinary lyric lines rarely carry a short non-narrative `<name>：` prefix, this
     * catches uncredited guest vocalists and band members while rejecting dialogue (`她说：...`)
     * and one-off poetic colons.
     */
    private fun discoverUnlistedColonSolos(
        lines: List<LyricLine>,
        artistMatcher: ArtistMatcher
    ): Set<String> {
        val standaloneCandidates = LinkedHashSet<String>()
        val inlineCandidateCounts = LinkedHashMap<String, Int>()
        var hasKnownExplicitSolo = false

        for (line in lines) {
            val match = LEADING_COLON_SPEAKER_REGEX.find(line.text) ?: continue
            val colonPos = match.groupValues[0].indexOfFirst { it == ':' || it == '：' || it == '∶' }
            val isClockLike = colonPos > 0 &&
                match.groupValues[0][colonPos - 1].isDigit() &&
                colonPos + 1 < line.text.length && line.text[colonPos + 1].isDigit()
            if (isClockLike) continue

            val rawLabel = match.groupValues[1].ifEmpty { match.groupValues[2] }
            val norm = normalizeSpeakerLabel(rawLabel)
            if (norm.isEmpty()) continue
            if (norm in GROUP_SPEAKER_LABELS) continue
            if (norm in SOLO_CANONICAL_MAP || resolveArtistToken(norm, artistMatcher, allowShortArtistAlias = true) != null) {
                hasKnownExplicitSolo = true
                continue
            }
            if (COMPOSITE_SPEAKER_SPLIT_REGEX.containsMatchIn(norm)) continue
            if (!isColonSpeakerCandidate(norm)) continue

            val remainder = line.text.substring(match.range.last + 1)
            if (remainder.isBlank()) {
                standaloneCandidates.add(norm)
            } else {
                inlineCandidateCounts[norm] = (inlineCandidateCounts[norm] ?: 0) + 1
            }
        }

        val accepted = LinkedHashSet<String>(standaloneCandidates)
        val hasAnchorSolo = hasKnownExplicitSolo || standaloneCandidates.isNotEmpty()
        val inlineTotalLines = inlineCandidateCounts.values.sum()

        if (inlineCandidateCounts.isNotEmpty()) {
            val allowInline = when {
                hasAnchorSolo -> true
                inlineCandidateCounts.size >= 2 && inlineTotalLines >= 3 -> true
                else -> false
            }
            if (allowInline) {
                accepted.addAll(inlineCandidateCounts.keys)
            }
        }
        return accepted
    }

    private fun isColonSpeakerCandidate(normLabel: String): Boolean {
        if (normLabel.isEmpty() || normLabel.length > MAX_STANDALONE_CUE_LABEL_LENGTH) return false
        if (normLabel.count { it == ' ' } > 2) return false
        if (normLabel.all { it.isDigit() }) return false
        if (SENTENCE_PUNCT_REGEX.containsMatchIn(normLabel)) return false
        if (NARRATIVE_SPEAKER_EXCLUSION_REGEX.containsMatchIn(normLabel)) return false
        if (NARRATIVE_SPEECH_SUFFIX_REGEX.containsMatchIn(normLabel)) return false
        if (normLabel in SINGLE_CHAR_FORBIDDEN_SPEAKERS) return false
        if (normLabel in SECTION_MARKER_LABELS) return false
        if (OnlineLyricInfoFilter.isCreditLabel(normLabel, hasDuetMarkers = true)) return false
        return true
    }

    private fun parseLineSpeakerPrefix(
        text: String,
        artistMatcher: ArtistMatcher,
        discoveredCueSolos: Set<String> = emptySet()
    ): ParsedSpeakerPrefix? {
        if (text.isBlank()) return null

        // 1. Colon-terminated speaker prefix: `男：...`, `（男）：...`, `林俊杰: ...`, `阿信：`
        val colonMatch = LEADING_COLON_SPEAKER_REGEX.find(text)
        if (colonMatch != null) {
            val rawLabel = colonMatch.groupValues[1].ifEmpty { colonMatch.groupValues[2] }
            val colonPos = colonMatch.groupValues[0].indexOfFirst { it == ':' || it == '：' || it == '∶' }
            val isClockLike = colonPos > 0 &&
                colonMatch.groupValues[0][colonPos - 1].isDigit() &&
                colonPos + 1 < text.length && text[colonPos + 1].isDigit()
            if (!isClockLike) {
                val classified = classifySpeakerLabel(
                    rawLabel = rawLabel,
                    artistMatcher = artistMatcher,
                    discoveredCueSolos = discoveredCueSolos,
                    allowShortArtistAlias = true
                )
                if (classified != null) {
                    return ParsedSpeakerPrefix(
                        kind = classified.first,
                        canonicalId = classified.second,
                        prefixEndIndex = colonMatch.range.last + 1
                    )
                }
            }
        }

        // 2. Bracketed speaker prefix without colon: `（男）...`, `(女) ...`, `【合】...`, `[Male] ...`
        val bracketMatch = LEADING_BRACKET_SPEAKER_REGEX.find(text)
        if (bracketMatch != null) {
            val rawLabel = bracketMatch.groupValues[1]
            val classified = classifySpeakerLabel(
                rawLabel = rawLabel,
                artistMatcher = artistMatcher,
                discoveredCueSolos = discoveredCueSolos,
                allowShortArtistAlias = false
            )
            if (classified != null) {
                return ParsedSpeakerPrefix(
                    kind = classified.first,
                    canonicalId = classified.second,
                    prefixEndIndex = bracketMatch.range.last + 1
                )
            }
        }

        return null
    }

    private fun stripMatchingSidecarPrefix(
        sidecarText: String?,
        artistMatcher: ArtistMatcher,
        discoveredCueSolos: Set<String>
    ): String? {
        if (sidecarText.isNullOrBlank()) return sidecarText
        val parsed = parseLineSpeakerPrefix(sidecarText, artistMatcher, discoveredCueSolos) ?: return sidecarText
        val stripped = sidecarText.substring(parsed.prefixEndIndex)
        return stripped.ifBlank { sidecarText }
    }

    private fun normalizeSpeakerLabel(rawLabel: String): String {
        return OnlineLyricInfoFilter.normalizeForMatch(rawLabel)
            .removePrefix("(").removeSuffix(")")
            .removePrefix("[").removeSuffix("]")
            .removePrefix("【").removeSuffix("】")
            .trim()
    }

    private fun classifySpeakerLabel(
        rawLabel: String,
        artistMatcher: ArtistMatcher,
        discoveredCueSolos: Set<String>,
        allowShortArtistAlias: Boolean
    ): Pair<SpeakerKind, String>? {
        val norm = normalizeSpeakerLabel(rawLabel)
        if (norm.isEmpty() || norm.length > MAX_SPEAKER_LABEL_LENGTH) return null
        if (NARRATIVE_SPEAKER_EXCLUSION_REGEX.containsMatchIn(norm)) return null

        // Built-in group / ensemble labels
        if (norm in GROUP_SPEAKER_LABELS) {
            return SpeakerKind.GROUP to "group"
        }

        // Built-in solo gender / voice labels
        SOLO_CANONICAL_MAP[norm]?.let { canonical ->
            return SpeakerKind.SOLO to canonical
        }

        // Composite speaker labels such as `林俊杰/蔡卓妍`, `周杰伦+阿信`, `周+费`, or `男+女`
        val compositeParts = norm.split(COMPOSITE_SPEAKER_SPLIT_REGEX)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
        if (compositeParts.size >= 2) {
            val resolvedParts = compositeParts.mapNotNull { part ->
                SOLO_CANONICAL_MAP[part]
                    ?: resolveArtistToken(part, artistMatcher, allowShortArtistAlias)?.let { "artist:$it" }
                    ?: part.takeIf { it in discoveredCueSolos }?.let { "cue:$it" }
            }
            if (resolvedParts.size == compositeParts.size && resolvedParts.toSet().size >= 2) {
                return SpeakerKind.GROUP to "group"
            }
        }

        // Named artist speaker prefix
        val artistCanonical = resolveArtistToken(norm, artistMatcher, allowShortArtistAlias)
        if (artistCanonical != null) {
            return SpeakerKind.SOLO to "artist:$artistCanonical"
        }

        // Discovered standalone cue speaker (e.g. `周杰伦：` / `阿信：`)
        if (norm in discoveredCueSolos) {
            return SpeakerKind.SOLO to "cue:$norm"
        }

        return null
    }

    private fun resolveArtistToken(
        normLabel: String,
        artistMatcher: ArtistMatcher,
        allowShortArtistAlias: Boolean
    ): String? {
        if (normLabel in artistMatcher.canonicalTokens) return normLabel
        artistMatcher.aliasToCanonical[normLabel]?.let { return it }
        if (allowShortArtistAlias && artistMatcher.isMultiArtistTrack && normLabel.length == 1) {
            if (normLabel in SINGLE_CHAR_FORBIDDEN_SPEAKERS) return null
            val matching = artistMatcher.canonicalTokens.filter { token ->
                token.length in 2..4 && isCjkString(token) && token.startsWith(normLabel)
            }
            if (matching.size == 1) {
                return matching.first()
            }
        }
        return null
    }

    private fun buildArtistMatcher(context: TrackFilterContext): ArtistMatcher {
        val featuredFromTitles = listOfNotNull(context.title, context.rawTitle, context.matchedTitle)
            .flatMap { rawTitle ->
                val normTitle = OnlineLyricInfoFilter.normalizeForMatch(rawTitle)
                TITLE_FEATURED_ARTIST_REGEX.findAll(normTitle)
                    .map { it.groupValues[1].trim() }
                    .filter { it.isNotEmpty() }
                    .toList()
            }
        val rawArtists = (listOfNotNull(context.artist, context.rawArtist, context.matchedArtist) + featuredFromTitles)
            .filter { it.isNotBlank() }
        if (rawArtists.isEmpty()) {
            return ArtistMatcher(emptySet(), emptyMap(), isMultiArtistTrack = false)
        }

        val canonicalTokens = LinkedHashSet<String>()
        val aliasToCanonical = LinkedHashMap<String, String>()

        for (raw in rawArtists) {
            val norm = OnlineLyricInfoFilter.normalizeForMatch(raw)
            val segments = norm.split(ARTIST_SPLIT_REGEX)
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            for (seg in segments) {
                val aliases = PAREN_CONTENT_REGEX.findAll(seg)
                    .map { it.groupValues[1].trim() }
                    .filter { it.length >= 2 }
                    .toList()
                val primary = seg.replace(PAREN_CONTENT_REGEX, " ")
                    .replace(MULTI_SPACE_REGEX, " ")
                    .trim()
                val canonical = primary.ifEmpty { seg }
                if (canonical.length >= 2) {
                    canonicalTokens.add(canonical)
                    aliasToCanonical[seg] = canonical
                    for (alias in aliases) {
                        aliasToCanonical.putIfAbsent(alias, canonical)
                    }
                    // For CJK artist names >= 3 chars (e.g., 周杰伦 -> 杰伦, 五月天阿信 -> 阿信),
                    // register the trailing 2-char suffix alias if it is not a generic word.
                    if (canonical.length >= 3 && isCjkString(canonical)) {
                        val suffixAlias = canonical.takeLast(2)
                        if (suffixAlias !in GENERIC_ALIAS_EXCLUSIONS) {
                            aliasToCanonical.putIfAbsent(suffixAlias, canonical)
                        }
                    }
                }
            }
        }

        // Remove ambiguous aliases that could map to multiple distinct canonical artists
        val validAliases = LinkedHashMap<String, String>()
        for ((alias, canonical) in aliasToCanonical) {
            if (alias == canonical) continue
            val owners = canonicalTokens.filter { token ->
                token == canonical || (alias.length >= 2 && token.contains(alias))
            }
            if (owners.size <= 1) {
                validAliases[alias] = canonical
            }
        }

        return ArtistMatcher(
            canonicalTokens = canonicalTokens,
            aliasToCanonical = validAliases,
            isMultiArtistTrack = canonicalTokens.size >= 2
        )
    }

    private fun isCjkString(text: String): Boolean =
        text.isNotEmpty() && text.all { ch -> ch in '\u4E00'..'\u9FFF' }

    /**
     * Trims exactly the stripped prefix characters from [syllables] so word-level sweep
     * character offsets remain 100% aligned with [newText].
     */
    internal fun trimLeadingPrefixFromSyllables(
        syllables: List<SyllableInfo>?,
        originalText: String,
        prefixLength: Int,
        newText: String
    ): List<SyllableInfo>? {
        if (syllables.isNullOrEmpty() || prefixLength <= 0) return syllables

        val joined = syllables.joinToString(separator = "") { it.text }
        val charsToTrim = when {
            joined == originalText -> prefixLength
            joined.endsWith(newText) && joined.length > newText.length -> joined.length - newText.length
            else -> return null
        }

        var remaining = charsToTrim
        val trimmed = ArrayList<SyllableInfo>(syllables.size)
        for (syllable in syllables) {
            if (remaining <= 0) {
                trimmed.add(syllable)
                continue
            }
            val len = syllable.text.length
            if (len <= remaining) {
                remaining -= len
            } else {
                val keptText = syllable.text.substring(remaining)
                val duration = (syllable.endTime - syllable.startTime).coerceAtLeast(0L)
                val shiftedStart = (syllable.startTime + duration * remaining / len)
                    .coerceAtMost(syllable.endTime)
                trimmed.add(
                    syllable.copy(
                        startTime = shiftedStart,
                        text = keptText
                    )
                )
                remaining = 0
            }
        }

        if (trimmed.isEmpty()) return null
        val verifiedText = trimmed.joinToString(separator = "") { it.text }
        return if (verifiedText == newText) trimmed else null
    }

    // ── XML & Time Helpers ─────────────────────────────────────────────────

    private fun parseXmlAttributes(raw: String): Map<String, String> {
        val attributes = mutableMapOf<String, String>()
        for (match in XML_ATTR_REGEX.findAll(raw)) {
            attributes[match.groupValues[1]] = match.groupValues[2]
        }
        return attributes
    }

    private fun stripXmlTags(text: String, separator: String = " "): String =
        text.replace(XML_TAG_REGEX, separator)

    private fun decodeXmlText(text: String): String =
        text.replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
            .replace("&#39;", "'")

    private fun parseFlexibleTimeToMs(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        val normalized = raw.trim().removeSuffix("s")
        val parts = normalized.split(":")
        return try {
            when (parts.size) {
                1 -> (parts[0].toDouble() * 1000).toLong()
                2 -> ((parts[0].toLong() * 60_000) + (parts[1].toDouble() * 1000)).toLong()
                3 -> ((parts[0].toLong() * 3_600_000) + (parts[1].toLong() * 60_000) + (parts[2].toDouble() * 1000)).toLong()
                else -> null
            }
        } catch (_: NumberFormatException) {
            null
        }
    }

    private fun normalizeTextForMatch(text: String): String =
        text.replace(MULTI_SPACE_REGEX, " ").trim()

    private const val TTML_TIME_TOLERANCE_MS = 150L
    private const val MAX_SPEAKER_LABEL_LENGTH = 24
    private const val MAX_STANDALONE_CUE_LABEL_LENGTH = 14

    private val TTML_AGENT_REGEX = Regex("""<(?:[A-Za-z0-9_.-]+:)?agent\b([^>]*)/?>""", RegexOption.IGNORE_CASE)
    private val TTML_P_REGEX = Regex("""<p\b([^>]*)>(.*?)</p>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val TTML_SPAN_REGEX = Regex("""<span\b([^>]*)>(.*?)</span>""", setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL))
    private val XML_ATTR_REGEX = Regex("""([A-Za-z_:][A-Za-z0-9_:\-.]*)="([^"]*)"""")
    private val XML_TAG_REGEX = Regex("""<[^>]+>""")
    private val MULTI_SPACE_REGEX = Regex("""\s+""")
    private val PAREN_CONTENT_REGEX = Regex("""\(([^)]+)\)""")
    private val SENTENCE_PUNCT_REGEX = Regex("""[。？！?!，,；;]""")
    private val ARTIST_SPLIT_REGEX = Regex("""(?:\s*(?:/|&|,|、|;|×|with|feat\.?|ft\.?)\s*)""", RegexOption.IGNORE_CASE)
    private val TITLE_FEATURED_ARTIST_REGEX = Regex(
        """(?:\(|\s)(?:with|feat\.?|ft\.?|featuring)\s+([^()\[\]【】]+)""",
        RegexOption.IGNORE_CASE
    )
    private val COMPOSITE_SPEAKER_SPLIT_REGEX = Regex("""\s*(?:/|\+|&|＆|、|和|与)\s*""")

    private val LEADING_COLON_SPEAKER_REGEX = Regex(
        """^\s*(?:[(（\[【]([^)）\]】]{1,20})[)）\]】]|([^:：∶\r\n]{1,20}))\s*[:：∶]\s*"""
    )
    private val LEADING_BRACKET_SPEAKER_REGEX = Regex(
        """^\s*[(（\[【]([^)）\]】]{1,16})[)）\]】]\s*"""
    )
    private val NARRATIVE_SPEAKER_EXCLUSION_REGEX = Regex(
        """^(?:我|你|他|她|它|我们|你们|他们|她们|谁|大家|有人|她说|我说|你说|他说|她问|我问|其实|然后|可是|但是|因为|所以|如果|只是|还是|也许|好像|记得|听见|看着)"""
    )
    private val NARRATIVE_SPEECH_SUFFIX_REGEX = Regex(
        """(?:说|问|答|道|喊|叫|唱|写|讲|叹|想|着|了|的|吗|呢|吧|啊|呀|says|said|asked|replied|whispered)$""",
        RegexOption.IGNORE_CASE
    )
    private val SINGLE_CHAR_FORBIDDEN_SPEAKERS = setOf(
        "词", "曲", "编", "唱", "混", "录", "鼓", "笛", "箫",
        "我", "你", "他", "她", "它", "谁", "这", "那", "一", "二", "三"
    )
    private val SECTION_MARKER_LABELS = setOf(
        "前奏", "间奏", "尾奏", "主歌", "副歌", "桥段", "过门", "引子", "尾声", "独白", "旁白", "念白",
        "intro", "outro", "verse", "verse 1", "verse 2", "pre-chorus", "bridge", "hook", "interlude", "instrumental"
    )
    private val GENERIC_ALIAS_EXCLUSIONS = setOf(
        "音乐", "乐队", "组合", "合唱", "歌手", "艺人", "团队", "工作室", "公司"
    )

    private val GROUP_SPEAKER_LABELS = setOf(
        "合", "齐", "合唱", "齐唱", "男女", "男女合", "男/女", "女/男", "男+女", "女+男",
        "男&女", "女&男", "众", "众人", "全", "全体", "all", "both", "duet", "chorus", "everyone", "together"
    )

    private val SOLO_CANONICAL_MAP = mapOf(
        "男" to "male",
        "男声" to "male",
        "男唱" to "male",
        "男主唱" to "male",
        "male" to "male",
        "man" to "male",
        "boy" to "male",
        "女" to "female",
        "女声" to "female",
        "女唱" to "female",
        "女主唱" to "female",
        "female" to "female",
        "woman" to "female",
        "girl" to "female",
        "童" to "child",
        "童声" to "child",
        "男1" to "male_1",
        "男2" to "male_2",
        "男a" to "male_1",
        "男b" to "male_2",
        "男甲" to "male_1",
        "男乙" to "male_2",
        "女1" to "female_1",
        "女2" to "female_2",
        "女a" to "female_1",
        "女b" to "female_2",
        "女甲" to "female_1",
        "女乙" to "female_2"
    )
}
