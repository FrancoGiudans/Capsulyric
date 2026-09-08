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

package com.example.islandlyrics.feature.update

object UpdateParser {
    private const val RELEASE_HIGHLIGHTS_HEADER = "## Release Highlights"
    private const val ENGLISH_HIGHLIGHTS_HEADER = "## English Highlights"
    private const val CN_HEADER = "## \uD83C\uDDE8\uD83C\uDDF3" // 🇨🇳
    private const val EN_HEADER = "## \uD83C\uDDEC\uD83C\uDDE7" // 🇬🇧
    private const val CN_LANGUAGE_MARKER = "\uD83C\uDDE8\uD83C\uDDF3" // 🇨🇳
    private const val EN_LANGUAGE_MARKER = "\uD83C\uDDEC\uD83C\uDDE7" // 🇬🇧
    private val H2_HEADER_REGEX = Regex("(?m)^##\\s+.+$")
    private val NESTED_LANGUAGE_HEADER_REGEX = Regex("(?m)^#{3,6}\\s+.+$")

    fun parseChangelog(rawBody: String?, isChinese: Boolean): String {
        if (rawBody.isNullOrBlank()) return ""

        val sections = extractSections(rawBody)
        if (!sections.hasLocalizedContent) {
            return rawBody.trim()
        }

        val localizedPart = if (isChinese) {
            sections.chinese.ifBlank { sections.english }
        } else {
            sections.english.ifBlank { sections.chinese }
        }

        return listOf(localizedPart, sections.shared)
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
            .trim()
    }

    internal fun extractSections(rawBody: String): ParsedSections {
        val headings = H2_HEADER_REGEX.findAll(rawBody).toList()
        if (headings.isEmpty()) {
            return ParsedSections(
                chinese = rawBody.trim(),
                english = "",
                shared = "",
                hasLocalizedContent = false
            )
        }

        val sections = headings.mapIndexed { index, match ->
            val end = headings.getOrNull(index + 1)?.range?.first ?: rawBody.length
            val heading = match.value.trim()
            val content = cleanSectionContent(
                rawBody.substring(match.range.last + 1, end)
                    .trimStart('\r', '\n')
            )
            MarkdownSection(
                heading = heading,
                content = content,
                raw = rawBody.substring(match.range.first, end).trim()
            )
        }

        val leadingContent = cleanSectionContent(rawBody.substring(0, headings.first().range.first))
        val releaseHighlightsIndex = sections.indexOfFirst {
            it.heading.startsWith(RELEASE_HIGHLIGHTS_HEADER)
        }
        if (releaseHighlightsIndex != -1) {
            val nestedHighlights = extractNestedHighlights(sections[releaseHighlightsIndex].content)
            if (nestedHighlights.hasLocalizedContent) {
                val sharedParts = buildList {
                    if (leadingContent.isNotBlank()) add(leadingContent)
                    sections.drop(releaseHighlightsIndex + 1)
                        .mapTo(this) { it.raw }
                }
                return ParsedSections(
                    chinese = nestedHighlights.chinese,
                    english = nestedHighlights.english,
                    shared = sharedParts.joinToString("\n\n").trim(),
                    hasLocalizedContent = true
                )
            }
        }

        val cnIndex = sections.indexOfFirst { isChineseHeading(it.heading) }
        val enIndex = sections.indexOfFirst { isEnglishHeading(it.heading) }
        val hasLocalized = cnIndex != -1 || enIndex != -1

        if (!hasLocalized) {
            return ParsedSections(
                chinese = rawBody.trim(),
                english = "",
                shared = "",
                hasLocalizedContent = false
            )
        }

        val lastLocalizedIndex = maxOf(cnIndex, enIndex)
        val sharedParts = buildList {
            if (leadingContent.isNotBlank()) add(leadingContent)
            sections.drop(lastLocalizedIndex + 1)
                .mapTo(this) { it.raw }
        }

        return ParsedSections(
            chinese = sections.getOrNull(cnIndex)?.content.orEmpty(),
            english = sections.getOrNull(enIndex)?.content.orEmpty(),
            shared = sharedParts.joinToString("\n\n").trim(),
            hasLocalizedContent = true
        )
    }

    private fun extractNestedHighlights(content: String): ParsedSections {
        val headings = NESTED_LANGUAGE_HEADER_REGEX.findAll(content).toList()
        if (headings.isEmpty()) {
            return ParsedSections(
                chinese = "",
                english = "",
                shared = "",
                hasLocalizedContent = false
            )
        }

        val commonContent = cleanSectionContent(content.substring(0, headings.first().range.first))
        val nestedSections = headings.mapIndexed { index, match ->
            val end = headings.getOrNull(index + 1)?.range?.first ?: content.length
            MarkdownSection(
                heading = match.value.trim(),
                content = cleanSectionContent(content.substring(match.range.last + 1, end)),
                raw = content.substring(match.range.first, end).trim()
            )
        }

        val chinese = nestedSections.firstOrNull { isChineseNestedHeading(it.heading) }?.content.orEmpty()
        val english = nestedSections.firstOrNull { isEnglishNestedHeading(it.heading) }?.content.orEmpty()
        val hasLocalized = chinese.isNotBlank() || english.isNotBlank()

        return ParsedSections(
            chinese = joinCommonHighlights(commonContent, chinese),
            english = joinCommonHighlights(commonContent, english),
            shared = "",
            hasLocalizedContent = hasLocalized
        )
    }

    private fun joinCommonHighlights(common: String, localized: String): String {
        return listOf(common, localized)
            .filter { it.isNotBlank() }
            .joinToString("\n\n")
            .trim()
    }

    private fun isChineseHeading(heading: String): Boolean {
        return heading.startsWith(CN_HEADER) ||
            heading.startsWith(RELEASE_HIGHLIGHTS_HEADER)
    }

    private fun isEnglishHeading(heading: String): Boolean {
        return heading.startsWith(EN_HEADER) ||
            heading.startsWith(ENGLISH_HIGHLIGHTS_HEADER)
    }

    private fun isChineseNestedHeading(heading: String): Boolean {
        val title = heading.trimStart('#').trim().lowercase()
        return heading.contains(CN_LANGUAGE_MARKER) ||
            title in setOf("cn", "zh", "中文", "chinese")
    }

    private fun isEnglishNestedHeading(heading: String): Boolean {
        val title = heading.trimStart('#').trim().lowercase()
        return heading.contains(EN_LANGUAGE_MARKER) ||
            title in setOf("en", "english")
    }

    private fun cleanSectionContent(content: String): String {
        val lines = content.trim().lines().toMutableList()
        while (lines.firstOrNull()?.let(::isHorizontalRule) == true) {
            lines.removeAt(0)
        }
        while (lines.lastOrNull()?.let(::isHorizontalRule) == true) {
            lines.removeAt(lines.lastIndex)
        }
        return lines.joinToString("\n").trim()
    }

    private fun isHorizontalRule(line: String): Boolean {
        val trimmed = line.trim()
        return trimmed.length >= 3 &&
            (
                trimmed.all { it == '-' } ||
                    trimmed.all { it == '*' } ||
                    trimmed.all { it == '_' }
                )
    }

    internal data class ParsedSections(
        val chinese: String,
        val english: String,
        val shared: String,
        val hasLocalizedContent: Boolean
    )

    private data class MarkdownSection(
        val heading: String,
        val content: String,
        val raw: String
    )
}
