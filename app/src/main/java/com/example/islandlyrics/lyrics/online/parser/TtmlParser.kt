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

// Ported from Lyricify-Lyrics-Helper (C# → Kotlin)
// (https://github.com/WXRIW/Lyricify-Lyrics-Helper)
// Copyright (C) WXRIW/Lyricify-Lyrics-Helper contributors
// Licensed under Apache-2.0

package com.example.islandlyrics.lyrics.online.parser

import com.example.islandlyrics.lyrics.online.OnlineLyricFetcher
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import org.xml.sax.InputSource
import java.io.StringReader
import java.util.Locale
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory
import kotlin.math.roundToLong

/**
 * Apple Music TTML 解析器（对齐 Lyricify-Lyrics-Helper v0.2.1 `TtmlParser.cs` + `TtmlParser.Pronunciation.cs`）。
 *
 * 支持特性：
 * - 区分主唱与 `ttm:role="x-bg"` 背景和声音节，避免背景人声打乱主歌词逐字时间轴；
 * - 支持 `<translation type="replacement">` 内嵌简体中文歌词替换（`zh-Hant` -> `zh-Hans`）；
 * - 支持 `<translation type="subtitle">` 翻译提取，并无条件剥离括号背景翻译 `(...)` / `（...）`
 *   （上游 `53a2f81d`：即使主行无对应 `x-bg` 音节也不让背景括号翻译混入主行）；
 * - 支持 `<transliterations>` 原生多语言注音/罗马音提取（上游 `5a0c26f9`：按源语言匹配、`-Latn` 优先与文档顺序选择默认发音轨）。
 */
internal object TtmlParser {

    internal data class TtmlParseResult(
        val lines: List<OnlineLyricFetcher.LyricLine>,
        val translationLrc: String? = null,
        val romanizationLrc: String? = null
    )

    private data class TranslationValue(
        val type: String,
        val lang: String,
        val text: String,
        val spanTexts: List<String>
    )

    private data class PronunciationPart(
        var text: String = "",
        val beginMs: Long? = null,
        val endMs: Long? = null,
        val clockOwner: Element? = null,
        val isWhitespaceGap: Boolean = false
    )

    private class PronunciationStream(
        val element: Element? = null,
        var hasContent: Boolean = false
    ) {
        val parts: MutableList<PronunciationPart> = mutableListOf()
    }

    private data class ParsedParagraph(
        val element: Element,
        val key: String?,
        val inheritedLang: String,
        val line: OnlineLyricFetcher.LyricLine,
        val isBackgroundOnlyLine: Boolean,
        val hasBackgroundVocals: Boolean
    )

    fun parse(
        ttml: String,
        useEmbeddedSimplifiedChineseLyrics: Boolean = true
    ): TtmlParseResult {
        if (ttml.isBlank() || ttml.length > MAX_DOCUMENT_CHARS) {
            return TtmlParseResult(emptyList())
        }

        val doc = parseSafeXmlDocument(ttml) ?: return TtmlParseResult(emptyList())
        val root = doc.documentElement ?: return TtmlParseResult(emptyList())
        if (!isElementNamed(root, "tt")) {
            return TtmlParseResult(emptyList())
        }

        val rootLanguage = getXmlLang(root)
        val translationsByKey = parseTranslations(root)
        val pElements = collectParagraphElements(root)
        if (pElements.isEmpty()) return TtmlParseResult(emptyList())

        val duplicateLineKeys = pElements
            .mapNotNull { getLineKey(it)?.takeIf(String::isNotBlank) }
            .groupingBy { it }
            .eachCount()
            .filterValues { it > 1 }
            .keys

        val parsedParagraphs = mutableListOf<ParsedParagraph>()
        val translationLines = mutableListOf<Pair<Long, String>>()

        for (p in pElements) {
            val key = getLineKey(p)
            val mainSyllables = mutableListOf<OnlineLyricFetcher.SyllableInfo>()
            val bgSyllables = mutableListOf<OnlineLyricFetcher.SyllableInfo>()
            collectSyllablesFromNodes(
                parent = p,
                main = mainSyllables,
                bg = bgSyllables,
                isBackgroundContext = false
            )

            val explicitBegin = parsePronunciationTimeMs(getAttr(p, "begin"))
            val explicitEnd = parsePronunciationTimeMs(getAttr(p, "end"))

            val isBgOnly: Boolean
            var builtLine: OnlineLyricFetcher.LyricLine? = when {
                mainSyllables.isNotEmpty() -> {
                    isBgOnly = false
                    val start = explicitBegin ?: mainSyllables.first().startTime
                    val end = explicitEnd ?: mainSyllables.maxOf { it.endTime }.coerceAtLeast(start)
                    OnlineLyricFetcher.LyricLine(
                        startTime = start,
                        endTime = end,
                        text = mainSyllables.joinToString("") { it.text }.replace(MULTI_SPACE_REGEX, " ").trim(),
                        syllables = resolveMissingSyllableEnds(mainSyllables, end)
                    )
                }
                bgSyllables.isNotEmpty() -> {
                    isBgOnly = true
                    normalizeBracketInnerSpacingForBgLyrics(bgSyllables)
                    val start = explicitBegin ?: bgSyllables.first().startTime
                    val end = explicitEnd ?: bgSyllables.maxOf { it.endTime }.coerceAtLeast(start)
                    OnlineLyricFetcher.LyricLine(
                        startTime = start,
                        endTime = end,
                        text = bgSyllables.joinToString("") { it.text }.replace(MULTI_SPACE_REGEX, " ").trim(),
                        syllables = resolveMissingSyllableEnds(bgSyllables, end)
                    )
                }
                else -> {
                    isBgOnly = false
                    val plainText = normalizeInlineText(p.textContent.orEmpty()).trim()
                    if (plainText.isNotEmpty() && explicitBegin != null) {
                        OnlineLyricFetcher.LyricLine(
                            startTime = explicitBegin,
                            endTime = explicitEnd ?: (explicitBegin + 5_000L),
                            text = plainText,
                            syllables = null
                        )
                    } else {
                        null
                    }
                }
            }

            if (builtLine == null || builtLine.text.isBlank()) continue

            val hasBgVocals = bgSyllables.isNotEmpty()
            val tList = if (!key.isNullOrBlank()) translationsByKey[key].orEmpty() else emptyList()
            if (tList.isNotEmpty()) {
                // 1. Replacement (e.g. Traditional -> Simplified Chinese)
                if (!isBgOnly) {
                    val replacement = tList.firstOrNull { entry ->
                        entry.type.equals("replacement", ignoreCase = true) &&
                            entry.text.isNotBlank() &&
                            (useEmbeddedSimplifiedChineseLyrics ||
                                !isLanguagePrefix(rootLanguage, "zh-Hant") ||
                                !isLanguagePrefix(entry.lang, "zh-Hans"))
                    }
                    if (replacement != null) {
                        builtLine = applyReplacementMainOnly(
                            line = builtLine,
                            replacement = replacement.text,
                            replacementParts = replacement.spanTexts,
                            hasBackgroundSubLine = hasBgVocals
                        )
                    }
                }

                // 2. Subtitle translations (with upstream 53a2f81d parenthesized bg stripping)
                val subtitles = tList.filter { it.type.equals("subtitle", ignoreCase = true) }
                if (subtitles.isNotEmpty()) {
                    val chosenSubtitle = selectPreferredSubtitle(subtitles)
                    if (chosenSubtitle != null) {
                        val (mainSub, bgSub) = splitSubtitleByParentheses(chosenSubtitle.text)
                        val effectiveSub = if (isBgOnly) {
                            bgSub ?: mainSub
                        } else {
                            mainSub
                        }
                        if (effectiveSub.isNotBlank()) {
                            translationLines += builtLine.startTime to effectiveSub
                        }
                    }
                }
            }

            parsedParagraphs += ParsedParagraph(
                element = p,
                key = key,
                inheritedLang = inheritedLanguage(p),
                line = builtLine,
                isBackgroundOnlyLine = isBgOnly,
                hasBackgroundVocals = hasBgVocals
            )
        }

        if (parsedParagraphs.isEmpty()) return TtmlParseResult(emptyList())

        val romanLines = extractPronunciationLines(
            root = root,
            paragraphs = parsedParagraphs,
            duplicateLineKeys = duplicateLineKeys
        )

        val sortedLines = parsedParagraphs
            .map { it.line }
            .sortedBy { it.startTime }

        return TtmlParseResult(
            lines = sortedLines,
            translationLrc = buildTimedSidecarLrc(translationLines),
            romanizationLrc = buildTimedSidecarLrc(romanLines)
        )
    }

    // ── Pronunciations / Transliterations (v0.2.1 `5a0c26f9`) ──────────────

    private fun extractPronunciationLines(
        root: Element,
        paragraphs: List<ParsedParagraph>,
        duplicateLineKeys: Set<String>
    ): List<Pair<Long, String>> {
        val head = findFirstChildElement(root, "head") ?: return emptyList()
        val metadata = findFirstChildElement(head, "metadata") ?: return emptyList()

        val containers = mutableListOf<Element>()
        for (child in childElements(metadata)) {
            when {
                isItunesOrLocal(child, "transliterations") -> containers += child
                isItunesOrLocal(child, "iTunesMetadata") -> {
                    childElements(child)
                        .filter { isItunesOrLocal(it, "transliterations") }
                        .forEach(containers::add)
                }
            }
        }
        if (containers.isEmpty()) return emptyList()

        val keyedParagraphs = paragraphs
            .filter { !it.key.isNullOrBlank() && it.key !in duplicateLineKeys }
            .groupBy { it.key!! }
            .filterValues { it.size == 1 }
            .mapValues { it.value.single() }
        if (keyedParagraphs.isEmpty()) return emptyList()

        // Guard against duplicate (forKey, lang) entries within transliteration tracks
        val entryCounts = mutableMapOf<Pair<String, String>, Int>()
        for (container in containers) {
            for (track in childElements(container)) {
                if (!isItunesOrLocal(track, "transliteration")) continue
                for (textNode in childElements(track)) {
                    if (!isItunesOrLocal(textNode, "text")) continue
                    val forKey = getAttr(textNode, "for")?.takeIf(String::isNotBlank) ?: continue
                    val langKey = inheritedLanguage(textNode).uppercase(Locale.ROOT)
                    val pair = forKey to langKey
                    entryCounts[pair] = (entryCounts[pair] ?: 0) + 1
                }
            }
        }
        val duplicateEntries = entryCounts.filterValues { it > 1 }.keys

        val languageOrder = mutableListOf<String>()
        // paragraph key -> (lang -> rendered pronunciation text)
        val pronunciationsByLineKey = linkedMapOf<String, LinkedHashMap<String, String>>()

        for (container in containers) {
            for (track in childElements(container)) {
                if (!isItunesOrLocal(track, "transliteration")) continue
                val trackLang = inheritedLanguage(track)
                if (languageOrder.none { it.equals(trackLang, ignoreCase = true) }) {
                    languageOrder += trackLang
                }
                for (textElement in childElements(track)) {
                    if (!isItunesOrLocal(textElement, "text")) continue
                    val key = getAttr(textElement, "for")?.takeIf(String::isNotBlank) ?: continue
                    val paragraph = keyedParagraphs[key] ?: continue
                    val entryLang = inheritedLanguage(textElement)
                    if ((key to entryLang.uppercase(Locale.ROOT)) in duplicateEntries) continue
                    if (languageOrder.none { it.equals(entryLang, ignoreCase = true) }) {
                        languageOrder += entryLang
                    }

                    val streams = readPronunciationStreams(textElement)
                    val chosenParts = if (paragraph.isBackgroundOnlyLine && streams.size > 1) {
                        streams.drop(1).flatMap { it.parts }
                    } else {
                        streams.firstOrNull()?.parts.orEmpty()
                    }
                    val rendered = chosenParts.joinToString("") { it.text }
                        .replace(MULTI_SPACE_REGEX, " ")
                        .trim()
                    if (rendered.isNotEmpty()) {
                        val langMap = pronunciationsByLineKey.getOrPut(key) { linkedMapOf() }
                        langMap[entryLang] = rendered
                    }
                }
            }
        }

        if (pronunciationsByLineKey.isEmpty()) return emptyList()

        val output = mutableListOf<Pair<Long, String>>()
        for (paragraph in paragraphs) {
            val key = paragraph.key ?: continue
            val availableMap = pronunciationsByLineKey[key] ?: continue
            val selectedLang = selectDefaultPronunciationLanguage(
                availableLanguages = availableMap.keys.toList(),
                sourceLanguage = paragraph.inheritedLang,
                languageOrder = languageOrder
            ) ?: continue
            val text = availableMap[selectedLang]?.trim().orEmpty()
            if (text.isNotEmpty()) {
                output += paragraph.line.startTime to text
            }
        }
        return output
    }

    private fun selectDefaultPronunciationLanguage(
        availableLanguages: List<String>,
        sourceLanguage: String,
        languageOrder: List<String>
    ): String? {
        if (availableLanguages.isEmpty()) return null
        val primary = sourceLanguage.substringBefore('-').trim()
        val matchingPrimary = if (primary.isNotEmpty()) {
            availableLanguages.filter { lang ->
                lang.substringBefore('-').equals(primary, ignoreCase = true)
            }
        } else {
            emptyList()
        }
        val candidates = matchingPrimary.ifEmpty { availableLanguages }
        return candidates.sortedWith(
            compareByDescending<String> { lang ->
                lang.split('-').any { it.equals("Latn", ignoreCase = true) }
            }.thenBy { lang ->
                val idx = languageOrder.indexOfFirst { it.equals(lang, ignoreCase = true) }
                if (idx >= 0) idx else Int.MAX_VALUE
            }
        ).firstOrNull()
    }

    private fun readPronunciationStreams(textElement: Element): List<PronunciationStream> {
        val mainStream = PronunciationStream()
        val streams = mutableListOf(mainStream)
        val unsupportedTiming = hasUnsupportedTimeContainerInAncestors(textElement)
        readPronunciationNodes(
            parent = textElement,
            stream = mainStream,
            streams = streams,
            beginMs = null,
            endMs = null,
            clockOwner = null,
            background = false,
            unsupportedTiming = unsupportedTiming
        )

        for (stream in streams) {
            for (i in stream.parts.indices.reversed()) {
                val part = stream.parts[i]
                if (!part.isWhitespaceGap) continue
                // Ignore pure XML pretty-print indentation containing line breaks;
                // preserve genuine horizontal word-separating spaces.
                if ('\n' in part.text || '\r' in part.text) {
                    stream.parts.removeAt(i)
                    continue
                }
                if (i > 0) {
                    stream.parts[i - 1].text += part.text
                    stream.parts.removeAt(i)
                } else if (stream.parts.size > 1) {
                    stream.parts[1].text = part.text + stream.parts[1].text
                    stream.parts.removeAt(i)
                }
            }
        }
        return streams
    }

    private fun readPronunciationNodes(
        parent: Node,
        stream: PronunciationStream,
        streams: MutableList<PronunciationStream>,
        beginMs: Long?,
        endMs: Long?,
        clockOwner: Element?,
        background: Boolean,
        unsupportedTiming: Boolean
    ) {
        val parentHasElementChildren = hasElementChildren(parent)
        val children = parent.childNodes
        for (idx in 0 until children.length) {
            val node = children.item(idx) ?: continue
            when (node.nodeType) {
                Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> {
                    val value = node.nodeValue.orEmpty()
                    stream.hasContent = true
                    val isGap = value.isBlank() && parentHasElementChildren
                    addPronunciationPart(
                        stream = stream,
                        text = if (isGap) value else normalizeInlineText(value),
                        beginMs = beginMs,
                        endMs = endMs,
                        clockOwner = clockOwner,
                        isWhitespaceGap = isGap
                    )
                }
                Node.ELEMENT_NODE -> {
                    val element = node as Element
                    val recognized = isTtmlOrItunesNamespace(element)
                    val local = localNameOf(element)
                    if (recognized && local == "br") {
                        stream.hasContent = true
                        addPronunciationPart(stream, " ", beginMs, endMs, clockOwner)
                        continue
                    }
                    val isSpan = recognized && local == "span"
                    val isBg = background || isBackgroundSpan(element)
                    val target = if (!background && isBg) {
                        PronunciationStream(element = element, hasContent = true).also(streams::add)
                    } else {
                        stream
                    }

                    var nextBegin = beginMs
                    var nextEnd = endMs
                    var nextOwner = clockOwner
                    var nextUnsupported = unsupportedTiming
                    if (isSpan) {
                        if (nextOwner == null) nextOwner = element
                        val beginAttr = getAttr(element, "begin")
                        val endAttr = getAttr(element, "end")
                        if (beginAttr != null || endAttr != null) {
                            nextOwner = element
                            if (beginAttr != null) nextBegin = parsePronunciationTimeMs(beginAttr)
                            if (endAttr != null) nextEnd = parsePronunciationTimeMs(endAttr)
                        }
                        val container = getAttr(element, "timeContainer")
                        if (container != null && !container.equals("par", ignoreCase = true)) {
                            nextUnsupported = true
                            nextOwner = element
                        }
                    }
                    if (nextUnsupported) {
                        nextBegin = null
                        nextEnd = null
                    }
                    if (!element.hasChildNodes()) {
                        target.hasContent = true
                        addPronunciationPart(target, "", nextBegin, nextEnd, nextOwner)
                    } else {
                        readPronunciationNodes(
                            parent = element,
                            stream = target,
                            streams = streams,
                            beginMs = nextBegin,
                            endMs = nextEnd,
                            clockOwner = nextOwner,
                            background = isBg,
                            unsupportedTiming = nextUnsupported
                        )
                    }
                    if (!isBg) stream.hasContent = true
                }
            }
        }
    }

    private fun addPronunciationPart(
        stream: PronunciationStream,
        text: String,
        beginMs: Long?,
        endMs: Long?,
        clockOwner: Element?,
        isWhitespaceGap: Boolean = false
    ) {
        val last = stream.parts.lastOrNull()
        if (last != null &&
            last.clockOwner === clockOwner &&
            last.beginMs == beginMs &&
            last.endMs == endMs &&
            last.isWhitespaceGap == isWhitespaceGap
        ) {
            last.text += text
        } else {
            stream.parts += PronunciationPart(
                text = text,
                beginMs = beginMs,
                endMs = endMs,
                clockOwner = clockOwner,
                isWhitespaceGap = isWhitespaceGap
            )
        }
    }

    // ── Syllables & Translations (`TtmlParser.cs`) ─────────────────────────

    private data class RawSyllable(
        var text: String,
        val startMs: Long,
        val endMs: Long?
    )

    private fun collectSyllablesFromNodes(
        parent: Node,
        main: MutableList<OnlineLyricFetcher.SyllableInfo>,
        bg: MutableList<OnlineLyricFetcher.SyllableInfo>,
        isBackgroundContext: Boolean
    ) {
        val rawMain = mutableListOf<RawSyllable>()
        val rawBg = mutableListOf<RawSyllable>()
        collectRawSyllables(parent, rawMain, rawBg, isBackgroundContext)
        main += rawMain.map { OnlineLyricFetcher.SyllableInfo(it.startMs, it.endMs ?: -1L, it.text) }
        bg += rawBg.map { OnlineLyricFetcher.SyllableInfo(it.startMs, it.endMs ?: -1L, it.text) }
    }

    private fun collectRawSyllables(
        parent: Node,
        main: MutableList<RawSyllable>,
        bg: MutableList<RawSyllable>,
        isBackgroundContext: Boolean
    ) {
        val parentHasElements = hasElementChildren(parent)
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i) ?: continue
            when (node.nodeType) {
                Node.TEXT_NODE, Node.CDATA_SECTION_NODE -> {
                    val raw = node.nodeValue.orEmpty()
                    // Skip XML pretty-print indentation between sibling elements
                    if (parentHasElements && raw.isBlank() && ('\n' in raw || '\r' in raw)) {
                        continue
                    }
                    val cleaned = normalizeInlineText(raw)
                    val target = if (isBackgroundContext) bg else main
                    if (cleaned.isNotEmpty() && target.isNotEmpty()) {
                        target[target.lastIndex].text += cleaned
                    }
                }
                Node.ELEMENT_NODE -> {
                    val el = node as Element
                    if (localNameOf(el) == "span") {
                        val isBg = isBackgroundContext || isBackgroundSpan(el)
                        val beginAttr = getAttr(el, "begin")
                        if (!beginAttr.isNullOrBlank()) {
                            val beginMs = parsePronunciationTimeMs(beginAttr)
                            val endMs = parsePronunciationTimeMs(getAttr(el, "end"))
                            if (beginMs != null) {
                                val target = if (isBg) bg else main
                                var text = normalizeInlineText(el.textContent.orEmpty())
                                text = moveLeadingSpacesToPrevious(text, target)
                                if (text.isNotEmpty()) {
                                    target += RawSyllable(text = text, startMs = beginMs, endMs = endMs)
                                }
                            }
                        } else {
                            collectRawSyllables(el, main, bg, isBg)
                        }
                    } else {
                        collectRawSyllables(el, main, bg, isBackgroundContext)
                    }
                }
            }
        }
    }

    private fun resolveMissingSyllableEnds(
        syllables: List<OnlineLyricFetcher.SyllableInfo>,
        lineEndMs: Long
    ): List<OnlineLyricFetcher.SyllableInfo> {
        return syllables.mapIndexed { index, item ->
            if (item.endTime >= item.startTime) {
                item
            } else {
                val fallbackEnd = syllables.getOrNull(index + 1)?.startTime
                    ?: lineEndMs.takeIf { it >= item.startTime }
                    ?: (item.startTime + 500L)
                item.copy(endTime = fallbackEnd.coerceAtLeast(item.startTime))
            }
        }
    }

    private fun moveLeadingSpacesToPrevious(
        text: String,
        list: MutableList<RawSyllable>
    ): String {
        if (text.isEmpty() || list.isEmpty()) return text
        var idx = 0
        while (idx < text.length && text[idx].isWhitespace()) idx++
        if (idx == 0) return text
        list[list.lastIndex].text += text.substring(0, idx)
        return text.substring(idx)
    }

    private fun normalizeBracketInnerSpacingForBgLyrics(
        syllables: MutableList<OnlineLyricFetcher.SyllableInfo>
    ) {
        if (syllables.isEmpty()) return
        val first = syllables.first()
        syllables[0] = first.copy(text = first.text.replace(Regex("""\s+([（(])"""), "$1"))
        val lastIdx = syllables.lastIndex
        val last = syllables[lastIdx]
        syllables[lastIdx] = last.copy(text = last.text.replace(Regex("""([）)])\s+"""), "$1"))
    }

    private fun parseTranslations(root: Element): Map<String, List<TranslationValue>> {
        val result = linkedMapOf<String, MutableList<TranslationValue>>()
        val allElements = root.getElementsByTagName("*")
        for (i in 0 until allElements.length) {
            val el = allElements.item(i) as? Element ?: continue
            if (!isItunesOrLocal(el, "translation")) continue
            val type = getAttr(el, "type")?.trim().orEmpty()
            val lang = inheritedLanguage(el)
            for (textEl in childElements(el)) {
                if (!isItunesOrLocal(textEl, "text")) continue
                val key = getAttr(textEl, "for")?.trim().orEmpty()
                if (key.isEmpty()) continue
                val value = normalizeInlineText(textEl.textContent.orEmpty()).trim()
                if (value.isEmpty()) continue
                val spanTexts = childElements(textEl)
                    .filter { localNameOf(it) == "span" && !getAttr(it, "begin").isNullOrBlank() }
                    .map { normalizeInlineText(it.textContent.orEmpty()) }
                    .filter { it.isNotEmpty() }
                result.getOrPut(key) { mutableListOf() } += TranslationValue(
                    type = type,
                    lang = lang,
                    text = value,
                    spanTexts = spanTexts
                )
            }
        }
        return result
    }

    private fun selectPreferredSubtitle(subtitles: List<TranslationValue>): TranslationValue? {
        if (subtitles.isEmpty()) return null
        return subtitles.sortedBy { entry ->
            val lang = entry.lang.lowercase(Locale.ROOT)
            when {
                lang.startsWith("zh-hans") || lang == "zh-cn" -> 0
                lang.startsWith("zh") -> 1
                else -> 2
            }
        }.firstOrNull()
    }

    /**
     * Upstream `53a2f81d`: Parenthesized background translations never belong to
     * the main line, even when the TTML has no corresponding `x-bg` syllables.
     */
    internal fun splitSubtitleByParentheses(value: String): Pair<String, String?> {
        val normalized = normalizeInlineText(value)
        val match = SUBTITLE_PAREN_REGEX.find(normalized)
            ?: return normalized.replace(MULTI_SPACE_REGEX, " ").trim() to null

        val inner = (match.groups[1]?.value ?: match.groups[2]?.value).orEmpty()
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()
        val main = normalized.removeRange(match.range)
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()
        return main to inner.ifBlank { null }
    }

    private fun applyReplacementMainOnly(
        line: OnlineLyricFetcher.LyricLine,
        replacement: String,
        replacementParts: List<String>,
        hasBackgroundSubLine: Boolean
    ): OnlineLyricFetcher.LyricLine {
        val normalized = normalizeInlineText(replacement)
        val mainReplacement = if (hasBackgroundSubLine) {
            splitSubtitleByParentheses(normalized).first
        } else {
            normalized.replace(MULTI_SPACE_REGEX, " ").trim()
        }
        if (mainReplacement.isBlank()) return line

        val syllables = line.syllables
        if (syllables.isNullOrEmpty()) {
            return line.copy(text = mainReplacement)
        }

        if (!hasBackgroundSubLine && replacementParts.size == syllables.size) {
            val mapped = syllables.mapIndexed { index, syl ->
                val leading = LEADING_WS_REGEX.find(syl.text)?.value.orEmpty()
                val trailing = TRAILING_WS_REGEX.find(syl.text)?.value.orEmpty()
                syl.copy(text = leading + normalizeInlineText(replacementParts[index]) + trailing)
            }
            return line.copy(
                text = mapped.joinToString("") { it.text }.replace(MULTI_SPACE_REGEX, " ").trim(),
                syllables = mapped
            )
        }

        val totalLen = syllables.sumOf { it.text.length }
        if (totalLen > 0 && totalLen == mainReplacement.length) {
            var offset = 0
            val mapped = syllables.map { syl ->
                val len = syl.text.length
                val chunk = mainReplacement.substring(offset, offset + len)
                offset += len
                syl.copy(text = chunk)
            }
            return line.copy(text = mainReplacement, syllables = mapped)
        }

        return line.copy(
            text = mainReplacement,
            syllables = listOf(
                OnlineLyricFetcher.SyllableInfo(line.startTime, line.endTime, mainReplacement)
            )
        )
    }

    // ── XML & Time Utilities ───────────────────────────────────────────────

    private fun parseSafeXmlDocument(ttml: String): Document? {
        return try {
            val factory = DocumentBuilderFactory.newInstance().apply {
                isNamespaceAware = true
                isValidating = false
                isXIncludeAware = false
                isExpandEntityReferences = false
                setFeatureSafe(this, XMLConstants.FEATURE_SECURE_PROCESSING, true)
                setFeatureSafe(this, "http://apache.org/xml/features/disallow-doctype-decl", true)
                setFeatureSafe(this, "http://xml.org/sax/features/external-general-entities", false)
                setFeatureSafe(this, "http://xml.org/sax/features/external-parameter-entities", false)
                setFeatureSafe(this, "http://apache.org/xml/features/nonvalidating/load-external-dtd", false)
            }
            val builder = factory.newDocumentBuilder()
            val doc = builder.parse(InputSource(StringReader(ttml)))
            if (!validateNodeAndDepthBudget(doc.documentElement)) null else doc
        } catch (_: Exception) {
            null
        }
    }

    private fun setFeatureSafe(factory: DocumentBuilderFactory, name: String, value: Boolean) {
        try {
            factory.setFeature(name, value)
        } catch (_: Throwable) {
        }
    }

    private fun validateNodeAndDepthBudget(root: Element?): Boolean {
        if (root == null) return false
        val stack = ArrayDeque<Pair<Node, Int>>()
        stack.addLast(root to 0)
        var count = 0
        while (stack.isNotEmpty()) {
            val (node, depth) = stack.removeLast()
            count++
            if (count > MAX_DOM_NODES || depth > MAX_DOM_DEPTH) return false
            val children = node.childNodes
            for (i in children.length - 1 downTo 0) {
                children.item(i)?.let { stack.addLast(it to (depth + 1)) }
            }
        }
        return true
    }

    private fun collectParagraphElements(root: Element): List<Element> {
        val list = mutableListOf<Element>()
        val nodes = root.getElementsByTagName("*")
        for (i in 0 until nodes.length) {
            val el = nodes.item(i) as? Element ?: continue
            if (localNameOf(el) == "p") {
                list += el
            }
        }
        return list
    }

    private fun childElements(parent: Node): List<Element> {
        val children = parent.childNodes
        val result = mutableListOf<Element>()
        for (i in 0 until children.length) {
            val item = children.item(i)
            if (item is Element) result += item
        }
        return result
    }

    private fun findFirstChildElement(parent: Node, localName: String): Element? =
        childElements(parent).firstOrNull { localNameOf(it) == localName }

    private fun hasElementChildren(node: Node): Boolean {
        val children = node.childNodes
        for (i in 0 until children.length) {
            if (children.item(i)?.nodeType == Node.ELEMENT_NODE) return true
        }
        return false
    }

    private fun localNameOf(el: Element): String =
        el.localName?.takeIf { it.isNotBlank() }
            ?: el.tagName.substringAfter(':')

    private fun isElementNamed(el: Element, expectedLocal: String): Boolean =
        localNameOf(el).equals(expectedLocal, ignoreCase = true)

    private fun isTtmlOrItunesNamespace(el: Element): Boolean {
        val ns = el.namespaceURI
        return ns.isNullOrBlank() || ns == NS_TTML || ns == NS_ITUNES
    }

    private fun isItunesOrLocal(el: Element, local: String): Boolean {
        if (localNameOf(el) != local) return false
        val ns = el.namespaceURI
        return ns.isNullOrBlank() || ns == NS_ITUNES || ns == NS_TTML
    }

    private fun isBackgroundSpan(el: Element): Boolean {
        val local = localNameOf(el)
        if (local != "span") return false
        val role = el.getAttributeNS(NS_TTM, "role").takeIf { it.isNotBlank() }
            ?: getAttr(el, "ttm:role")
            ?: getAttr(el, "role")
        return role.equals("x-bg", ignoreCase = true)
    }

    private fun getLineKey(el: Element): String? =
        el.getAttributeNS(NS_ITUNES, "key").takeIf { it.isNotBlank() }
            ?: getAttr(el, "itunes:key")
            ?: getAttr(el, "key")

    private fun getXmlLang(el: Element): String =
        (el.getAttributeNS(NS_XML, "lang").takeIf { it.isNotBlank() }
            ?: getAttr(el, "xml:lang")
            ?: getAttr(el, "lang"))
            ?.trim()
            .orEmpty()

    private fun inheritedLanguage(el: Element): String {
        var current: Node? = el
        while (current is Element) {
            val lang = getXmlLang(current)
            if (lang.isNotEmpty()) return lang
            current = current.parentNode
        }
        return ""
    }

    private fun hasUnsupportedTimeContainerInAncestors(el: Element): Boolean {
        var current: Node? = el
        while (current is Element) {
            val container = getAttr(current, "timeContainer")
            if (container != null && !container.equals("par", ignoreCase = true)) {
                return true
            }
            current = current.parentNode
        }
        return false
    }

    private fun getAttr(el: Element, name: String): String? {
        if (el.hasAttribute(name)) {
            return el.getAttribute(name)
        }
        val attrs = el.attributes ?: return null
        val targetLocal = name.substringAfter(':')
        for (i in 0 until attrs.length) {
            val item = attrs.item(i) ?: continue
            val nodeName = item.nodeName.orEmpty()
            val itemLocal = item.localName?.takeIf { it.isNotBlank() } ?: nodeName.substringAfter(':')
            if (nodeName == name || (!name.contains(':') && itemLocal == targetLocal)) {
                return item.nodeValue
            }
        }
        return null
    }

    private fun isLanguagePrefix(lang: String?, prefix: String): Boolean {
        if (lang.isNullOrBlank()) return false
        val trimmed = lang.trim()
        return trimmed.equals(prefix, ignoreCase = true) ||
            trimmed.startsWith("$prefix-", ignoreCase = true)
    }

    internal fun parsePronunciationTimeMs(raw: String?): Long? {
        if (raw == null) return null
        var value = raw.trim()
        if (value.isEmpty()) return null

        val milliseconds: Double = if (value.endsWith("ms", ignoreCase = true)) {
            parseStrictNumber(value.dropLast(2)) ?: return null
        } else {
            if (value.endsWith("s", ignoreCase = true)) {
                value = value.dropLast(1)
            }
            val parts = value.split(':')
            if (parts.isEmpty() || parts.size > 3) return null
            var seconds = 0.0
            for (i in parts.indices) {
                val component = parseStrictNumber(parts[i]) ?: return null
                if (parts.size > 1 && i > 0 && component >= 60.0) return null
                if (i < parts.lastIndex && component != kotlin.math.truncate(component)) return null
                seconds = seconds * 60.0 + component
            }
            seconds * 1000.0
        }

        if (milliseconds.isNaN() || milliseconds.isInfinite() ||
            milliseconds < 0.0 || milliseconds > Int.MAX_VALUE.toDouble()
        ) {
            return null
        }
        return milliseconds.roundToLong()
    }

    private fun parseStrictNumber(value: String): Double? {
        if (value.isEmpty() || !value.all { it.isDigit() || it == '.' }) return null
        val parsed = value.toDoubleOrNull() ?: return null
        return parsed.takeIf { !it.isNaN() && !it.isInfinite() }
    }

    private fun normalizeInlineText(text: String): String =
        text.replace("\r", "").replace("\n", "")

    private fun buildTimedSidecarLrc(entries: List<Pair<Long, String>>): String? {
        if (entries.isEmpty()) return null
        return entries
            .sortedBy { it.first }
            .joinToString("\n") { (timeMs, text) ->
                "${formatLrcTimestamp(timeMs)}$text"
            }
            .takeIf { it.isNotBlank() }
    }

    private fun formatLrcTimestamp(timeMs: Long): String {
        val clamped = timeMs.coerceAtLeast(0L)
        val minutes = clamped / 60_000L
        val seconds = (clamped % 60_000L) / 1_000L
        val millis = clamped % 1_000L
        return String.format(Locale.ROOT, "[%02d:%02d.%03d]", minutes, seconds, millis)
    }

    private const val NS_TTML = "http://www.w3.org/ns/ttml"
    private const val NS_TTM = "http://www.w3.org/ns/ttml#metadata"
    private const val NS_ITUNES = "http://music.apple.com/lyric-ttml-internal"
    private const val NS_XML = "http://www.w3.org/XML/1998/namespace"

    private const val MAX_DOCUMENT_CHARS = 4 * 1024 * 1024
    private const val MAX_DOM_NODES = 200_000
    private const val MAX_DOM_DEPTH = 128

    private val SUBTITLE_PAREN_REGEX = Regex("""\(([^)]*)\)|（([^）]*)）""")
    private val MULTI_SPACE_REGEX = Regex("""\s+""")
    private val LEADING_WS_REGEX = Regex("""^\s+""")
    private val TRAILING_WS_REGEX = Regex("""\s+$""")
}
