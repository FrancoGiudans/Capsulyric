package com.example.islandlyrics

/**
 * Data class representing a notification parser rule for a music app.
 * 
 * @param packageName The app's package name (e.g., "com.tencent.qqmusic")
 * @param enabled Whether this rule is active
 * @param usesCarProtocol Whether to parse notifications using car protocol
 * @param separatorPattern The separator to split artist/title (e.g., "-", " - ", " | ")
 * @param fieldOrder The order of fields in the notification (ARTIST_TITLE or TITLE_ARTIST)
 */
data class ParserRule(
    val packageName: String,
    val enabled: Boolean = true,
    val usesCarProtocol: Boolean = true,
    val separatorPattern: String = "-",  // Default: tight hyphen
    val fieldOrder: FieldOrder = FieldOrder.ARTIST_TITLE
) : Comparable<ParserRule> {
    override fun compareTo(other: ParserRule): Int {
        return packageName.compareTo(other.packageName)
    }
}

/**
 * Field order in notification text parsing.
 * 
 * ARTIST_TITLE: "Artist-Title" → extract as (title=Title, artist=Artist)
 * TITLE_ARTIST: "Title-Artist" → extract as (title=Title, artist=Artist)
 */
enum class FieldOrder {
    ARTIST_TITLE,  // Most common: "周杰伦-晴天" → title="晴天", artist="周杰伦"
    TITLE_ARTIST   // Less common: "晴天-周杰伦" → title="晴天", artist="周杰伦"
}
