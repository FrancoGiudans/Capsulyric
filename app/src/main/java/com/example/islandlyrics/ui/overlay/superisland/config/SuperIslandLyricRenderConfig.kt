package com.example.islandlyrics.ui.overlay.superisland.config
internal data class SuperIslandLyricRenderConfig(
    val lyricMode: String,
    val fullLyricShowLeftCover: Boolean,
    val rightTextWeight: Int,
    val leftWithCoverTextWeight: Int,
    val leftNoCoverTextWeight: Int
)

internal fun SuperIslandPreferencesCache.toLyricRenderConfig(): SuperIslandLyricRenderConfig {
    return SuperIslandLyricRenderConfig(
        lyricMode = lyricMode,
        fullLyricShowLeftCover = fullLyricShowLeftCover,
        rightTextWeight = rightTextWeight,
        leftWithCoverTextWeight = leftWithCoverTextWeight,
        leftNoCoverTextWeight = leftNoCoverTextWeight
    )
}
