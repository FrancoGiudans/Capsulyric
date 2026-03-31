package com.example.islandlyrics.feature.update

object UpdateParser {
    private const val CN_HEADER = "## \uD83C\uDDE8\uD83C\uDDF3" // 🇨🇳
    private const val EN_HEADER = "## \uD83C\uDDEC\uD83C\uDDE7" // 🇬🇧

    fun parseChangelog(rawBody: String?, isChinese: Boolean): String {
        if (rawBody.isNullOrBlank()) return ""

        val cnStart = rawBody.indexOf(CN_HEADER)
        val enStart = rawBody.indexOf(EN_HEADER)

        val displayText = if (cnStart != -1 && enStart != -1) {
            val secondStart = maxOf(cnStart, enStart)

            // Find a potential common footer starting with "---" after both localized headers
            val commonSeparator = "---"
            val commonStart = rawBody.indexOf(commonSeparator, secondStart)

            val localizedPart = if (isChinese) {
                if (cnStart < enStart) {
                    rawBody.substring(cnStart + CN_HEADER.length, enStart).trim()
                } else {
                    if (commonStart != -1) rawBody.substring(cnStart + CN_HEADER.length, commonStart).trim()
                    else rawBody.substring(cnStart + CN_HEADER.length).trim()
                }
            } else {
                if (enStart < cnStart) {
                    rawBody.substring(enStart + EN_HEADER.length, cnStart).trim()
                } else {
                    if (commonStart != -1) rawBody.substring(enStart + EN_HEADER.length, commonStart).trim()
                    else rawBody.substring(enStart + EN_HEADER.length).trim()
                }
            }

            val commonPart = if (commonStart != -1) rawBody.substring(commonStart).trim() else ""
            if (commonPart.isNotEmpty()) "$localizedPart\n\n$commonPart" else localizedPart
        } else {
            rawBody
        }

        // Final cleaning
        return displayText
            .replace(Regex("^\\s*更新日志\\s*", RegexOption.MULTILINE), "")
            .replace(Regex("^\\s*Change Log\\s*", RegexOption.MULTILINE), "")
            .trim()
    }
}
