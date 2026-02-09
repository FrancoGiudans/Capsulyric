package com.example.islandlyrics

/**
 * Semantic title parser that extracts clean song names and secondary information
 * from various title formats.
 */
data class ParsedTitle(
    val primaryLine: String,      // Clean song name for line 1
    val secondaryInfo: String      // Extra info (e.g., "Remastered 2021") or empty
)

object TitleParser {
    
    /**
     * Parse title into primary and secondary components.
     * 
     * Handles multiple title formats:
     * 1. Standard with brackets: "Beautiful World (Remastered 2021)" 
     *    → Primary: "Beautiful World", Secondary: "Remastered 2021"
     * 2. Bilingual: "未行之路 The Road Not Taken"
     *    → Primary: "未行之路", Secondary: "The Road Not Taken"
     * 3. Complex or short: Returns full title as primary, empty secondary
     */
    fun parse(title: String): ParsedTitle {
        val trimmed = title.trim()
        
        // Strategy 1: Extract bracket content
        val bracketMatch = Regex("""^(.+?)\s*\(([^)]+)\)\s*$""").find(trimmed)
        if (bracketMatch != null) {
            val mainTitle = bracketMatch.groupValues[1].trim()
            val bracketContent = bracketMatch.groupValues[2].trim()
            return ParsedTitle(mainTitle, bracketContent)
        }
        
        // Strategy 2: Detect bilingual (CJK followed by Latin/Western)
        // Find the boundary where CJK characters end and Latin begins
        val bilingualSplit = findLanguageBoundary(trimmed)
        if (bilingualSplit != null) {
            return bilingualSplit
        }
        
        // Strategy 3: No clear split - return full title
        return ParsedTitle(trimmed, "")
    }
    
    /**
     * Find the boundary between CJK and Latin characters in a string.
     * Returns ParsedTitle if a clear boundary is found, null otherwise.
     */
    private fun findLanguageBoundary(text: String): ParsedTitle? {
        if (text.isEmpty()) return null
        
        // Track indices where language changes
        var lastWasCJK = isCJK(text[0])
        var boundaryIndex = -1
        
        for (i in 1 until text.length) {
            val currentIsCJK = isCJK(text[i])
            
            // Detect transition from CJK to non-CJK (ignoring whitespace)
            if (lastWasCJK && !currentIsCJK && !text[i].isWhitespace()) {
                boundaryIndex = i
                break
            }
            
            // Update state (skip whitespace for state tracking)
            if (!text[i].isWhitespace()) {
                lastWasCJK = currentIsCJK
            }
        }
        
        // If we found a clear boundary and both parts have substance
        if (boundaryIndex > 0 && boundaryIndex < text.length - 1) {
            val firstPart = text.substring(0, boundaryIndex).trim()
            val secondPart = text.substring(boundaryIndex).trim()
            
            // Validate both parts are non-empty and substantial
            if (firstPart.length >= 2 && secondPart.length >= 2) {
                return ParsedTitle(firstPart, secondPart)
            }
        }
        
        return null
    }
    
    /**
     * Check if a character is CJK (Chinese, Japanese, Korean).
     */
    private fun isCJK(c: Char): Boolean {
        val block = Character.UnicodeBlock.of(c) ?: return false
        return block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS ||
               block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A ||
               block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B ||
               block == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS ||
               block == Character.UnicodeBlock.HIRAGANA ||
               block == Character.UnicodeBlock.KATAKANA ||
               block == Character.UnicodeBlock.HANGUL_SYLLABLES
    }
}
