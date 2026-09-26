package com.example.islandlyrics.feature.update

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateParserTest {

    @Test
    fun parseChangelog_nestedReleaseHighlights_selectsChineseAndKeepsSharedChanges() {
        val rawBody = """
            ## Release Highlights
            <img src="logo.png" width="100%" />

            ### 🇨🇳
            - 中文重点

            ### 🇬🇧
            - English highlight

            ---

            ## Release Metadata
            - **Version:** `26.9.Stable_C1234`

            ---

            ## Changes
            ### Features
            - Generated feature
        """.trimIndent()

        val parsed = UpdateParser.parseChangelog(rawBody, isChinese = true)

        assertTrue(parsed.contains("<img src=\"logo.png\" width=\"100%\" />"))
        assertTrue(parsed.contains("- 中文重点"))
        assertTrue(parsed.contains("## Release Metadata"))
        assertTrue(parsed.contains("## Changes"))
        assertFalse(parsed.contains("- English highlight"))
        assertFalse(parsed.contains("### 🇨🇳"))
        assertFalse(parsed.contains("### 🇬🇧"))
    }

    @Test
    fun parseChangelog_nestedReleaseHighlights_selectsEnglishAndKeepsSharedChanges() {
        val rawBody = """
            ## Release Highlights
            <img src="logo.png" width="100%" />

            ### 🇨🇳
            - 中文重点

            ### 🇬🇧
            - English highlight

            ---

            ## Changes
            ### Fixes
            - Generated fix
        """.trimIndent()

        val parsed = UpdateParser.parseChangelog(rawBody, isChinese = false)

        assertTrue(parsed.contains("<img src=\"logo.png\" width=\"100%\" />"))
        assertTrue(parsed.contains("- English highlight"))
        assertTrue(parsed.contains("## Changes"))
        assertFalse(parsed.contains("- 中文重点"))
    }

    @Test
    fun parseChangelog_legacyFlagHeadings_stillWork() {
        val rawBody = """
            ## 🇨🇳
            - 中文旧格式

            ---

            ## 🇬🇧
            - English legacy

            ---

            ## Changes
            - Generated change
        """.trimIndent()

        val parsed = UpdateParser.parseChangelog(rawBody, isChinese = true)

        assertTrue(parsed.contains("- 中文旧格式"))
        assertTrue(parsed.contains("## Changes"))
        assertFalse(parsed.contains("- English legacy"))
    }

    @Test
    fun parseChangelog_releaseAndEnglishHighlightsHeadings_stillWork() {
        val rawBody = """
            ## Release Highlights
            - 中文当前格式

            ---

            ## English Highlights
            - English current format

            ---

            ## Changes
            - Generated change
        """.trimIndent()

        val parsed = UpdateParser.parseChangelog(rawBody, isChinese = false)

        assertTrue(parsed.contains("- English current format"))
        assertTrue(parsed.contains("## Changes"))
        assertFalse(parsed.contains("- 中文当前格式"))
    }
}
