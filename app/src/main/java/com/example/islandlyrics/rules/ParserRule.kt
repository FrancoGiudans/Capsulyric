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

package com.example.islandlyrics.rules

/**
 * Data class representing a notification parser rule for a music app.
 * 
 * @param packageName The app's package name (e.g., "com.tencent.qqmusic")
 * @param enabled Whether this rule is active
 * @param usesCarProtocol Whether to parse notifications using car protocol
 * @param separatorPattern The separator to split artist/title (e.g., "-", " - ", " | ")
 * @param fieldOrder The order of fields in the notification (ARTIST_TITLE or TITLE_ARTIST)
 * @param customName User-defined friendly name (e.g., "QQ Music"). If null, falls back to package name.
 */
data class ParserRule(
    val packageName: String,
    val customName: String? = null,
    val enabled: Boolean = true,
    val usesCarProtocol: Boolean = true,
    val separatorPattern: String = "-",  // Default: tight hyphen
    val fieldOrder: FieldOrder = FieldOrder.TITLE_ARTIST,
    
    // Lyric source settings (per-app)
    val useOnlineLyrics: Boolean = false,      // Whether to fetch lyrics from online APIs
    val useSmartOnlineLyricSelection: Boolean = true,
    val useRawMetadataForOnlineMatching: Boolean = false,
    val receiveOnlineTranslation: Boolean = false,
    val receiveOnlineRomanization: Boolean = false,
    val onlineLyricProviderOrder: List<String> = emptyList(),
    val onlineLyricDisabledProviders: Set<String> = emptySet(),
    val useSuperLyricApi: Boolean = false,     // Whether to use SuperLyric API callbacks
    val useLyricGetterApi: Boolean = false,    // Whether to use Lyric Getter API broadcasts
    val useLyriconApi: Boolean = false,        // Whether to subscribe to Lyricon active-player lyrics
    val receiveLyriconTranslation: Boolean = false,
    val receiveLyriconRomanization: Boolean = false,
    val useLocalLyrics: Boolean = false,        // Whether to search local .lrc files first
    val useLastFmScrobble: Boolean = false      // Whether to scrobble this app to Last.fm
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
