package com.example.islandlyrics.feature.update

object UpdateParser {
    private const val CN_HEADER = "## \uD83C\uDDE8\uD83C\uDDF3" // 🇨🇳
    private const val EN_HEADER = "## \uD83C\uDDEC\uD83C\uDDE7" // 🇬🇧
    private val H2_HEADER_REGEX = Regex("(?m)^##\\s+.+$")

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
            val content = rawBody.substring(match.range.last + 1, end)
                .trimStart('\r', '\n')
                .trim()
            MarkdownSection(
                heading = heading,
                content = content,
                raw = rawBody.substring(match.range.first, end).trim()
            )
        }

        val firstHeadingStart = headings.first().range.first
        val leadingContent = rawBody.substring(0, firstHeadingStart).trim()
        val cnIndex = sections.indexOfFirst { it.heading.startsWith(CN_HEADER) }
        val enIndex = sections.indexOfFirst { it.heading.startsWith(EN_HEADER) }
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
